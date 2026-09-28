package com.nocturne.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.data.AppState
import com.nocturne.iptv.ui.theme.NocturnePalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppState,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onLoadEpg: (String) -> Unit,
    onOpenGuide: () -> Unit
) {
    var epg by remember { mutableStateOf(state.epgLoadedFor.orEmpty()) }

    Scaffold(
        containerColor = NocturnePalette.Abyss,
        topBar = {
            TopAppBar(
                title = { Text("Cellar", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NocturnePalette.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NocturnePalette.Crypt,
                    titleContentColor = NocturnePalette.Bone
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Card(
                colors = CardDefaults.cardColors(containerColor = NocturnePalette.Crypt),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Guide (EPG)", style = MaterialTheme.typography.titleLarge, color = NocturnePalette.Bone)
                    Text(
                        "Point Nocturne at an XMLTV file to fill the channels with now/next titles.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NocturnePalette.Ash
                    )
                    OutlinedTextField(
                        value = epg,
                        onValueChange = { epg = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("XMLTV URL") },
                        placeholder = { Text("https://…/guide.xml") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { if (epg.isNotBlank()) onLoadEpg(epg.trim()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NocturnePalette.Blood,
                                contentColor = NocturnePalette.Bone
                            ),
                            enabled = !state.loading
                        ) { Text("Load guide") }

                        OutlinedButton(onClick = onOpenGuide) {
                            Text("View guide")
                        }
                    }
                    if (state.epg.isNotEmpty()) {
                        Text(
                            "Loaded ${state.epg.values.sumOf { it.size }} programmes across ${state.epg.size} channels.",
                            style = MaterialTheme.typography.labelMedium,
                            color = NocturnePalette.Ember
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = NocturnePalette.Crypt),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sources", style = MaterialTheme.typography.titleLarge, color = NocturnePalette.Bone)
                    Text(
                        "${state.playlists.size} source(s) · ${state.channels.size} channels in the active set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NocturnePalette.Ash
                    )
                    Button(
                        onClick = onAddPlaylist,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NocturnePalette.Blood,
                            contentColor = NocturnePalette.Bone
                        )
                    ) { Text("Manage sources") }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = NocturnePalette.Crypt),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.titleLarge, color = NocturnePalette.Bone)
                    Text(
                        "Nocturne is a media player. It does not host, index, or provide any streams. " +
                            "You are responsible for the content you load and for having the rights to view it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NocturnePalette.Ash
                    )
                    Text(
                        "Formats: HLS (.m3u8), MPEG-TS (.ts), DASH (.mpd), progressive (.m3u/.mp4).",
                        style = MaterialTheme.typography.labelMedium,
                        color = NocturnePalette.Ash
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium, color = NocturnePalette.Ember)
                }
            }
        }
    }
}