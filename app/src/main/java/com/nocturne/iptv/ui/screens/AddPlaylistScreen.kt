package com.nocturne.iptv.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.data.AppState
import com.nocturne.iptv.data.PlaylistSource
import com.nocturne.iptv.ui.theme.NocturnePalette

private enum class ImportMode { URL, PASTE, FILE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    state: AppState,
    onBack: () -> Unit,
    onImportUrl: (String, String?, String?) -> Unit,
    onImportText: (String, String?, String?) -> Unit,
    onImportUri: (Uri, String?, String?) -> Unit,
    onOpenPlaylist: (PlaylistSource) -> Unit,
    onRemovePlaylist: (String) -> Unit,
    onClearError: () -> Unit
) {
    var mode by remember { mutableStateOf(ImportMode.URL) }
    var url by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportUri(uri, label.ifBlank { null }, epgUrl.ifBlank { null })
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            onClearError()
        }
    }

    Scaffold(
        containerColor = NocturnePalette.Abyss,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Feed the source", style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Nocturne plays whatever you bring. It ships empty — no channels, no links.",
                style = MaterialTheme.typography.bodyMedium,
                color = NocturnePalette.Ash
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ImportMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ImportMode.entries.size),
                        icon = {
                            val icon = when (m) {
                                ImportMode.URL -> Icons.Filled.Link
                                ImportMode.PASTE -> Icons.Filled.ContentPaste
                                ImportMode.FILE -> Icons.Filled.UploadFile
                            }
                            Icon(icon, contentDescription = null)
                        }
                    ) {
                        Text(m.name)
                    }
                }
            }

            when (mode) {
                ImportMode.URL -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Playlist URL") },
                        placeholder = { Text("https://…/playlist.m3u") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                ImportMode.PASTE -> {
                    OutlinedTextField(
                        value = pasted,
                        onValueChange = { pasted = it },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        label = { Text("Raw M3U / M3U8 text") },
                        placeholder = { Text("#EXTM3U\n#EXTINF:-1,My Channel\nhttp://…/stream.m3u8") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                ImportMode.FILE -> {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NocturnePalette.Ember)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose .m3u / .m3u8 file")
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label (optional)") },
                placeholder = { Text("e.g. Living Room") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = epgUrl,
                onValueChange = { epgUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("XMLTV EPG URL (optional)") },
                placeholder = { Text("https://…/guide.xml") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    when (mode) {
                        ImportMode.URL -> if (url.isNotBlank()) onImportUrl(url.trim(), label.ifBlank { null }, epgUrl.ifBlank { null })
                        ImportMode.PASTE -> if (pasted.isNotBlank()) onImportText(pasted, label.ifBlank { null }, epgUrl.ifBlank { null })
                        ImportMode.FILE -> filePicker.launch(arrayOf("*/*"))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NocturnePalette.Blood,
                    contentColor = NocturnePalette.Bone
                ),
                enabled = !state.loading
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        color = NocturnePalette.Bone,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text("Summon channels", style = MaterialTheme.typography.labelLarge)
            }

            if (state.playlists.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Imported sources",
                    style = MaterialTheme.typography.titleMedium,
                    color = NocturnePalette.Bone
                )
                state.playlists.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                source.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = NocturnePalette.Bone,
                                maxLines = 1
                            )
                            Text(
                                "${source.channelCount} channels · ${source.kind.name.lowercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = NocturnePalette.Ash
                            )
                        }
                        OutlinedButton(onClick = { onOpenPlaylist(source) }) {
                            Text("Open")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { onRemovePlaylist(source.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NocturnePalette.Ember)
                        ) {
                            Text("Forget")
                        }
                    }
                }
            }

            Box(modifier = Modifier.height(24.dp))
        }
    }
}