package com.nocturne.iptv.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FadeIn
import androidx.compose.animation.core.FadeOut
import androidx.compose.foundation.Background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.intPx
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Add
import androidx.compose.material3.icons.filled.Menu
import androidx.compose.material3.icons.outlined.Close
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.data.EpgLookup
import com.nocturne.iptv.data.AppState
import com.nocturne.iptv.data.Models
import com.nocturne.iptv.ui.components.ChannelRow
import com.nocturne.iptv.ui.theme.NocturnePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

/* -------------------------------------------------
   1️⃣  Core state that the screen already uses
   ------------------------------------------------- */
private enum class HomeTab(val label: String) { ALL("Channels"), VAULT("Vault"), RECENT("Recent"), GUIDE("Guide") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppState,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    /* ---- existing vars --------------------------------------------------- */
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val favorites = state.favorites
    val byId = remember(state.channels) { state.channels.associateBy { it.id } }
    var urlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    /* ---- filtered channel list (unchanged) ------------------------------- */
    val filtered: List<Channel> = remember(state.channels, tab, group, query, favorites, state.recents) {
        val base = when (HomeTab.entries[tab]) {
            HomeTab.ALL -> state.channels
            HomeTab.VAULT -> state.channels.filter { favorites.contains(it.id) }
            HomeTab.RECENT -> state.recents.mapNotNull { byId[it] }
            HomeTab.GUIDE -> emptyList()
        }
        val byGroup = if (tab == 0 && group != null) base.filter { it.group == group } else base
        if (query.isBlank()) byGroup
        else byGroup.filter { it.name.contains(query, ignoreCase = true) || it.group.contains(query, ignoreCase = true) }
    }

    /* -------------------------------------------------
       2️⃣  URL‑handler dialog
       ------------------------------------------------- */
    // "URL" FAB – opens the dialog
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = { urlDialog = true },
            icon = { Icon(Icons.Outlined.Link, contentDescription = "URL") },
            label = { Text("URL") },
            colors = FabDefaults.colors(
                containerColor = NocturnePalette.Blood,
                pressedIndicatorColor = NocturnePalette.Ember
            )
        )
    }

    // Dialog that asks for a URL and offers two actions
    if (urlDialog) {
        val builder = AlertDialog.Builder(LocalContext.current)
        var urlInDialog = inputUrl

        builder.setTitle("Paste a stream URL")
        builder.setView(
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = urlInDialog,
                    onValueChange = { urlInDialog = it },
                    placeholder = { Text("e.g. https://…/m3u8 or rtmp://…") },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            urlDialog = false
                            // play inside the app (HLS/DASH/MP4)
                            val u = uriParser(urlInDialog)
                            if (u != null) {
                                playInside(u)
                            } else {
                                // fall‑back to external player
                                openExternal(urlInDialog)
                            }
                        },
                        text = "Play inside"
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            urlDialog = false
                            openExternal(urlInDialog)
                        },
                        text = "Open in player"
                    )
                }
            }
        )
        builder.show()
    }

    /* -------------------------------------------------
       3️⃣  Scheme parser – returns a Uri if the scheme
            we support, otherwise null.
       ------------------------------------------------- */
    private fun uriParser(url: String): Uri? {
        try {
            val u = Uri.parse(url)
            // support HLS, DASH, progressive MP4, MP3, TS, etc.
            when (u.scheme) {
                "m3u8", "hls", "mpd", "mp4", "m4v", "mp3", "wav", "ogg", "flac", "webm", "ts" -> return u
                else -> return null // let the external‑player branch handle it
            }
        } catch (e: Exception) {
            return null
        }
    }

    /* -------------------------------------------------
       4️⃣  Play inside the app (ExoPlayer).  This re‑uses the
           existing “play” logic from MainActivity.
       ------------------------------------------------- */
    private fun playInside(url: Uri) {
        // Navigate to the existing PlayerActivity – you can also
        // reuse the PlayerScreen composable if you prefer.
        val ctx = LocalContext.current
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", url.toString())
        })
    }

    /* -------------------------------------------------
       5️⃣  Open in an external player (VLC / MX Player /
           generic “view‑in‑browser”).
       ------------------------------------------------- */
    private fun openExternal(url: String) {
        val u = Uri.parse(url)
        val scheme = u.scheme

        when (scheme) {
            "rtmp" -> {
                // Try VLC first, then MX Player, then a generic intent
                val vlc = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/x-rtmp")
                    .setPackage("org.videolan.vlc")
                val mx = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(u, "application/x-rtmp")
                    .setPackage("com.mxplayer.player")
                try { LocalContext.current.startActivity(vlc) }
                catch (_: android.content.ActivityNotFoundException) {
                    try { LocalContext.current.startActivity(mx) }
                    catch (_) {
                        // final fallback – let the system decide
                        val intent = new Intent(Intent.ACTION_VIEW).setData(u)
                        LocalContext.current.startActivity(intent)
                    }
                }
            }
            "tvbus", "mitv", "p8p", "vjms" -> {
                // These are custom schemes – just launch a generic intent
                val intent = new Intent(Intent.ACTION_VIEW).setData(u)
                try { LocalContext.current.startActivity(intent) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.Local(context = LocalContext.current, text = "Cannot handle this stream", duration = Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Anything else (e.g. http, https) – hand off to the system
                val intent = new Intent(Intent.ACTION_VIEW).setData(u)
                try { LocalContext.current.startActivity(intent) }
                catch (_: android.content.ActivityNotFoundException) {
                    Toast.Local(context = LocalContext.current, text = "No app to open this URL", duration = Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /* -------------------------------------------------
       6️⃣  Split‑screen layout
       ------------------------------------------------- */
    // The whole screen is now a Row: left = channel list, right = preview.
    Scaffold(
        drawerColumn = { /* we keep the default drawer closed – the FAB does the job */ },
        bottomBar = {
            BottomAppBar(
                leadingIcon = {
                    IconButton(onClick = { navController.navigate("home") }) {
                        Icon(Icons.Navigation.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    // The URL‑FAB is placed inside the main Column above;
                    // we keep the BottomAppBar minimal.
                },
                backgroundColor = NocturnePalette.Abyss,
                contentColor = NocturnePalette.Bone
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // left channel list takes ~30 %
                .weight(1f, fill = false) // will be sized by the weight modifier inside
        ) {
            /* ---------- LEFT: channel list ---------- */
            Box(
                modifier = Modifier
                    .weight(0.3f)          // 30 % of the width
                    .background(NocturnePalette.Crypt)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = favorites.contains(channel.id),
                            currentProgram = EpgLookup.current(state.epg, channel),
                            onClick = {
                                selectedChannel = channel
                                // The right‑hand preview will automatically update
                            }
                        )
                    }
                }
            }

            /* ---------- RIGHT: preview area ---------- */
            Box(
                modifier = Modifier
                    .weight(0.7f)          // 70 % of the width
                    .background(NocturnePalette.Abyss)
            ) {
                if (selectedChannel != null) {
                    // Small‑size player preview – we reuse the same ExoPlayer
                    // logic that PlayerActivity uses, but keep it tiny.
                    PlayerPreview(channel = selectedChannel)
                } else {
                    Text(
                        text = "Select a channel to preview",
                        color = NocturnePalette.Ash,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    } // end Scaffold
}

/* -------------------------------------------------
   7️⃣  Tiny composable that shows a mini‑player
   ------------------------------------------------- */
@Composable
private fun PlayerPreview(channel: Channel) {
    // We simply show the channel name + logo and a “Play” button.
    // If you want a real tiny ExoPlayer window you can embed an AndroidView
    // that references the same data source PlayerActivity uses.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(NocturnePalette.Crypt, shape = RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo (fallback placeholder)
        val logo = channel.logo ?: "📺"
        Text(
            text = logo,
            style = MaterialTheme.typography.displayLarge,
            color = NocturnePalette.Ember,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.headlineMedium,
            color = NocturnePalette.Bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                // jump to full‑screen player (same as tapping a channel in the old list)
                onPlay(channel)
            },
            style = MaterialTheme.buttonStyle,
            backgroundColor = NocturnePalette.Blood,
            textColor = NocturnePalette.Bone
        ) {
            Text("Watch live")
        }
    }
}
