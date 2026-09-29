package com.nocturne.iptv.player

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nocturne.iptv.NocturneApp
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.data.EpgLookup
import com.nocturne.iptv.player.source.NocturneDataSourceFactory
import com.nocturne.iptv.player.source.Transports
import com.nocturne.iptv.ui.components.HorrorOverlay
import com.nocturne.iptv.ui.components.rememberFlicker
import com.nocturne.iptv.ui.theme.NocturnePalette
import com.nocturne.iptv.ui.theme.NocturneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Fullscreen playback. The current queue lives on the application object
 * ([PlaybackSession]) so this activity only has to render and drive the player.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = buildPlayer()

        setContent {
            NocturneTheme {
                PlayerScreen(
                    player = requireNotNull(player),
                    session = (application as NocturneApp).playbackSession
                )
            }
        }
    }

    private fun buildPlayer(): ExoPlayer {
        // One routing factory for every transport: HLS/DASH/TS/MP4 stay on
        // stock ExoPlayer, rtmp goes to the RTMP extension, and exotic
        // schemes (tvbus/mitv/p8p/vjms) go to their registered bridges.
        val dataSourceFactory: DataSource.Factory = NocturneDataSourceFactory(this)
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply { playWhenReady = true }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(player: ExoPlayer, session: PlaybackSession) {
    val context = LocalContext.current
    val app = context.applicationContext as NocturneApp
    val state by app.repository.state.collectAsState()
    val scope = rememberCoroutineScope()

    var channel by remember { mutableStateOf(session.current) }
    var showControls by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var glitchAt by remember { mutableLongStateOf(0L) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // A channel may carry several stream URLs (multi-server playlists).
    // sourceIndex walks them: auto-advance on failure, manual cycle via SRC.
    var sourceIndex by remember { mutableIntStateOf(0) }
    var sources by remember { mutableStateOf(emptyList<String>()) }

    fun playSource(target: Channel, index: Int) {
        val list = target.allSources
        val safe = index.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        sourceIndex = safe
        failure = null
        buffering = true
        glitchAt = System.currentTimeMillis()
        player.setMediaItem(Transports.mediaItem(list[safe]))
        player.prepare()
        player.play()
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            delay(30_000)
        }
    }

    LaunchedEffect(channel?.id) {
        val target = channel ?: return@LaunchedEffect
        sources = target.allSources
        playSource(target, 0)
        app.repository.markWatched(target.id)
    }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }

            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                val target = channel
                val list = target?.allSources.orEmpty()
                if (target != null && sourceIndex + 1 < list.size) {
                    // Dead source — slip to the next one automatically, no drama.
                    playSource(target, sourceIndex + 1)
                } else {
                    failure = friendlyError(error)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(showControls, channel?.id) {
        if (showControls) {
            delay(4_500)
            showControls = false
        }
    }

    fun zap(delta: Int) {
        session.move(delta)
        channel = session.current
    }

    fun retry() {
        channel?.let { playSource(it, sourceIndex) }
    }

    fun cycleSource() {
        val target = channel ?: return
        if (sources.size < 2) return
        playSource(target, (sourceIndex + 1) % sources.size)
    }

    val transportLabel = Transports.label(sources.getOrNull(sourceIndex) ?: channel?.url.orEmpty())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    this.player = player
                }
            }
        )

        HorrorOverlay(
            modifier = Modifier.fillMaxSize(),
            vignetteStrength = 0.75f,
            scanlineAlpha = 0.12f
        )

        StaticFlash(trigger = glitchAt, modifier = Modifier.fillMaxSize())

        if (buffering && failure == null) {
            BufferingMark(modifier = Modifier.align(Alignment.Center))
        }

        failure?.let { message ->
            FailureCard(
                message = message,
                onRetry = { retry() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(320)),
            modifier = Modifier.fillMaxSize()
        ) {
            ControlsOverlay(
                channel = channel,
                isFavorite = channel?.let { state.favorites.contains(it.id) } == true,
                currentTitle = channel?.let { EpgLookup.current(state.epg, it, nowTick)?.title },
                nextTitle = channel?.let { EpgLookup.next(state.epg, it, nowTick)?.title },
                transportLabel = transportLabel,
                sourceIndex = sourceIndex,
                sourceCount = sources.size.coerceAtLeast(1),
                onBack = { (context as? PlayerActivity)?.finish() },
                onUp = { zap(-1) },
                onDown = { zap(1) },
                onCycleSource = { cycleSource() },
                onToggleFavorite = { channel?.let { ch -> scope.launch { app.repository.toggleFavorite(ch.id) } } }
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    channel: Channel?,
    isFavorite: Boolean,
    currentTitle: String?,
    nextTitle: String?,
    transportLabel: String,
    sourceIndex: Int,
    sourceCount: Int,
    onBack: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onCycleSource: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val flicker = rememberFlicker(min = 0.9f, max = 1f, periodMs = 260)

    Box(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NocturnePalette.Bone)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel?.name ?: "…",
                    style = MaterialTheme.typography.titleLarge,
                    color = NocturnePalette.Bone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                currentTitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = NocturnePalette.Ember,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favourite",
                    tint = if (isFavorite) NocturnePalette.Ember else NocturnePalette.Bone
                )
            }
            TransportChip(label = transportLabel)
            if (sourceCount > 1) {
                SourceChip(
                    label = "SRC ${sourceIndex + 1}/$sourceCount",
                    onClick = onCycleSource
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZapButton(icon = Icons.Filled.KeyboardArrowUp, label = "CH+", onClick = onUp)
            ZapButton(icon = Icons.Filled.KeyboardArrowDown, label = "CH-", onClick = onDown)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(12.dp)
        ) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelMedium,
                color = NocturnePalette.Ash,
                modifier = Modifier.alpha(flicker)
            )
            Text(
                text = currentTitle ?: "Unknown transmission",
                style = MaterialTheme.typography.bodyLarge,
                color = NocturnePalette.Bone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            nextTitle?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "NEXT  ·  $it",
                    style = MaterialTheme.typography.labelMedium,
                    color = NocturnePalette.Ember,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransportChip(label: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NocturnePalette.Coffin)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NocturnePalette.Ash,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SourceChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NocturnePalette.Blood),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NocturnePalette.Ember,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ZapButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, NocturnePalette.Coffin),
        modifier = Modifier.size(64.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Icon(icon, contentDescription = label, tint = NocturnePalette.Ember, modifier = Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = NocturnePalette.Ash)
        }
    }
}

/** Frame-by-frame white noise shown briefly after a channel change. */
@Composable
private fun StaticFlash(trigger: Long, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }

    LaunchedEffect(trigger) {
        if (trigger == 0L) return@LaunchedEffect
        visible = true
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 380) {
            frame++
            withFrameNanos { }
        }
        visible = false
    }

    if (!visible) return

    Canvas(modifier = modifier) {
        // `frame` is read to invalidate the canvas each tick.
        val seed = frame
        drawSaltAndPepper(alpha = 0.5f, seed = seed)
    }
}

private fun DrawScope.drawSaltAndPepper(alpha: Float, seed: Long) {
    val rng = Random(seed)
    val step = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            if (rng.nextFloat() > 0.55f) {
                drawRect(
                    color = Color.White.copy(alpha = alpha * rng.nextFloat()),
                    topLeft = Offset(x, y),
                    size = Size(step, step)
                )
            }
            x += step
        }
        y += step
    }
}

@Composable
private fun BufferingMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "buffer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "bufferAlpha"
    )
    Text(
        text = "…tuning…",
        style = MaterialTheme.typography.bodyLarge,
        color = NocturnePalette.Ember.copy(alpha = alpha),
        modifier = modifier
    )
}

@Composable
private fun FailureCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = NocturnePalette.Crypt,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, NocturnePalette.Blood)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("The signal died", style = MaterialTheme.typography.headlineMedium, color = NocturnePalette.Bone)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = NocturnePalette.Ash)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = NocturnePalette.Ember)
                }
                Text("Try again", color = NocturnePalette.Ember)
            }
        }
    }
}

private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Could not reach the stream host. Check your connection — or the link is dead."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "The host refused the request. The channel may be offline or restricted."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "The stream format could not be read. The playlist entry may be broken."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        "This device cannot decode the stream. The codec may be unsupported."
    else -> error.message ?: error.cause?.message ?: "An unknown error snuffed the signal."
}