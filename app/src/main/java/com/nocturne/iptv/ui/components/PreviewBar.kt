package com.nocturne.iptv.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.player.source.NocturneDataSourceFactory
import com.nocturne.iptv.player.source.Transports
import com.nocturne.iptv.ui.theme.NocturnePalette

/**
 * A 16:9 "spirit monitor" pinned above the channel list: a muted mini-player
 * tuned to the chosen channel (last watched, else the first signal), with
 * fullscreen, mute, and vault actions. Tap anywhere to go fullscreen.
 *
 * Callers must key this composable by channel id — `key(channel.id) { … }` —
 * so a new channel gets a fresh player and the old one is released.
 */
@OptIn(UnstableApi::class)
@Composable
fun PreviewBar(
    channel: Channel,
    isFavorite: Boolean,
    onOpenFullscreen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }

    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(NocturneDataSourceFactory(context))
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = true
                setMediaItem(Transports.mediaItem(channel.url))
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (!failed) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenFullscreen),
        color = NocturnePalette.Crypt,
        border = BorderStroke(1.dp, NocturnePalette.Blood)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlitchText(
                    text = "◉ PREVIEW",
                    style = MaterialTheme.typography.labelLarge,
                    glitchEveryMs = 3_000L..7_000L
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Transports.label(channel.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = NocturnePalette.Ash,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = if (muted) "Unmute preview" else "Mute preview",
                        tint = NocturnePalette.Ash
                    )
                }
                IconButton(onClick = onOpenFullscreen, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "Open fullscreen",
                        tint = NocturnePalette.Ember
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (!failed) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                setShutterBackgroundColor(AndroidColor.BLACK)
                            }
                        },
                        update = { it.player = player }
                    )
                } else {
                    PreviewPoster(channel = channel, modifier = Modifier.fillMaxSize())
                }

                HorrorOverlay(
                    modifier = Modifier.fillMaxSize(),
                    vignetteStrength = 0.6f,
                    scanlineAlpha = 0.14f
                )

                if (failed) {
                    Text(
                        text = "signal dead — tap for the crypt",
                        style = MaterialTheme.typography.labelMedium,
                        color = NocturnePalette.Ash,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = NocturnePalette.Bone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = channel.group,
                            style = MaterialTheme.typography.labelMedium,
                            color = NocturnePalette.Ash,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (channel.hasAlternates) {
                            Icon(
                                imageVector = Icons.Filled.Layers,
                                contentDescription = null,
                                tint = NocturnePalette.Ember,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${channel.allSources.size} sources",
                                style = MaterialTheme.typography.labelSmall,
                                color = NocturnePalette.Ember
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) "Remove from vault" else "Add to vault",
                        tint = if (isFavorite) NocturnePalette.Ember else NocturnePalette.Ash
                    )
                }
            }
        }
    }
}

/** Channel art shown in the monitor when the stream itself will not tune. */
@Composable
private fun PreviewPoster(channel: Channel, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(NocturnePalette.Abyss),
        contentAlignment = Alignment.Center
    ) {
        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = NocturnePalette.Blood,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
