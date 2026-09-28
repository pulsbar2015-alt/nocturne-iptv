import androidx.compose.material3.BorderStroke
package com.nocturne.iptv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.player.PlayerActivity
import com.nocturne.iptv.ui.theme.NocturnePalette
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * Simple URL-input screen. The user pastes any stream URL; the app either:
 *  – plays it internally via ExoPlayer (HLS/DASH/progressive MP4), or
 *  – delegates to an external player via an Android Intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlHandlerScreen(onBack: () -> Unit) {
    // ... rest of the file
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }

    Scaffold(
        containerColor = NocturnePalette.Abyss,
        topBar = {
            TopAppBar(
                title = { Text("Stream URL", color = NocturnePalette.Bone) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NocturnePalette.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NocturnePalette.Crypt)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NocturnePalette.Abyss)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Paste a stream URL below",
                style = MaterialTheme.typography.bodyMedium,
                color = NocturnePalette.Ash
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://host/stream.m3u8\nrtmp://live.example/channel\nhttp://host/file.ts") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NocturnePalette.Blood,
                    unfocusedBorderColor = NocturnePalette.Coffin,
                    cursorColor = NocturnePalette.Ember
                )
            )

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Play inside the app (ExoPlayer)
                Button(
                    onClick = { playInside(context, url.trim()) },
                    enabled = url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NocturnePalette.Blood),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Play inside", color = NocturnePalette.Bone)
                }

                // Open in external player
                OutlinedButton(
                    onClick = { openExternal(context, url.trim()) },
                    enabled = url.isNotBlank(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NocturnePalette.Bone),
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, NocturnePalette.Coffin)
                ) {
                    Text("Open externally", color = NocturnePalette.Ash)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick hint section
            Card(
                colors = CardDefaults.cardColors(containerColor = NocturnePalette.Crypt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Supported formats:", style = MaterialTheme.typography.labelMedium, color = NocturnePalette.Ember)
                    Text("• HLS  : *.m3u8, *.ts\n• DASH : *.mpd\n• MP4/MP3/TS : progressive\n• RTMP : launched via VLC\n• Other schemes : delegated to system", style = MaterialTheme.typography.bodySmall, color = NocturnePalette.Ash)
                }
            }
        }
    }
}

private fun playInside(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase() ?: ""
    // Play internally only for schemes ExoPlayer handles natively
    val playableSchemes = setOf("http", "https", "m3u8", "hls", "dash", "mpd", "rtsp")
    if (scheme !in playableSchemes && !url.contains(".") || scheme in setOf("tvbus", "mitv", "p8p", "vjms", "rtmp")) {
        openExternal(context, url)
        return
    }
    context.startActivity(Intent(context, PlayerActivity::class.java).apply {
        putExtra("EXTRA_STREAM_URL", url)
    })
}

private fun openExternal(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    val uri = Uri.parse(url)
    val scheme = uri.scheme?.lowercase() ?: ""
    when (scheme) {
        "rtmp" -> {
            val mimeType = "application/x-rtmp"
            val targets = listOf("org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro")
            var launched = false
            for (pkg in targets) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    setPackage(pkg)
                }
                try {
                    context.startActivity(intent)
                    launched = true
                    break
                } catch (_: android.content.ActivityNotFoundException) {
                    // try next
                }
            }
            if (!launched) {
                val fallback = Intent(Intent.ACTION_VIEW).setData(uri)
                context.startActivity(fallback)
            }
        }
        else -> {
            val intent = Intent(Intent.ACTION_VIEW).setData(uri)
            context.startActivity(intent)
        }
    }
}
