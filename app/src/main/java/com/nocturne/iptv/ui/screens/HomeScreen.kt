package com.nocturne.iptv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nocturne.iptv.data.AppState
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.data.EpgLookup
import com.nocturne.iptv.ui.components.ChannelRow
import com.nocturne.iptv.ui.components.EmptyState
import com.nocturne.iptv.ui.components.GlitchText
import com.nocturne.iptv.ui.components.GroupChip
import com.nocturne.iptv.ui.theme.NocturnePalette

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
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }

    val favorites = state.favorites
    val byId = remember(state.channels) { state.channels.associateBy { it.id } }

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

    Scaffold(
        containerColor = NocturnePalette.Abyss,
        topBar = {
            TopAppBar(
                title = {
                    GlitchText(
                        text = "NOCTURNE",
                        style = MaterialTheme.typography.headlineMedium,
                        glitchEveryMs = 4_000L..9_000L
                    )
                },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "Guide", tint = NocturnePalette.Ember)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = NocturnePalette.Ash)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NocturnePalette.Crypt,
                    titleContentColor = NocturnePalette.Bone
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlaylist,
                containerColor = NocturnePalette.Blood,
                contentColor = NocturnePalette.Bone,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("SOURCE", style = MaterialTheme.typography.labelLarge) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NocturnePalette.Abyss)
        ) {
            if (state.channels.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.loading) {
                        CircularProgressIndicator(color = NocturnePalette.Blood)
                    } else {
                        EmptyState(
                            title = "The vault is empty",
                            message = "Feed it a playlist. Paste an M3U URL, load a file, or drop raw text — we handle m3u8, ts and friends.",
                            action = {
                                ExtendedFloatingActionButton(
                                    onClick = onAddPlaylist,
                                    containerColor = NocturnePalette.Blood,
                                    contentColor = NocturnePalette.Bone,
                                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    text = { Text("Add source") }
                                )
                            }
                        )
                    }
                }
                return@Column
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search the dark…", color = NocturnePalette.Ash) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NocturnePalette.Ash) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            TabRow(
                selectedTabIndex = tab,
                containerColor = NocturnePalette.Crypt,
                contentColor = NocturnePalette.Bone,
                divider = {}
            ) {
                HomeTab.entries.forEachIndexed { i, entry ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i; if (i != 0) group = null },
                        text = {
                            Text(
                                entry.label,
                                color = if (tab == i) NocturnePalette.Ember else NocturnePalette.Ash,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        icon = {
                            val icon = when (entry) {
                                HomeTab.ALL -> Icons.Outlined.Tv
                                HomeTab.VAULT -> Icons.Filled.StarBorder
                                HomeTab.RECENT -> Icons.Outlined.History
                                HomeTab.GUIDE -> Icons.Outlined.CalendarMonth
                            }
                            Icon(icon, contentDescription = null, tint = if (tab == i) NocturnePalette.Ember else NocturnePalette.Ash)
                        }
                    )
                }
            }

            if (HomeTab.entries[tab] == HomeTab.GUIDE) {
                GuideTab(
                    state = state,
                    onPlay = onPlay,
                    onOpenGuide = onOpenGuide
                )
                return@Column
            }

            if (tab == 0 && state.groups.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                ) {
                    item {
                        GroupChip(
                            label = "All",
                            selected = group == null,
                            onClick = { group = null }
                        )
                    }
                    items(state.groups) { g ->
                        GroupChip(label = g, selected = group == g, onClick = { group = if (group == g) null else g })
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filtered.size} signals",
                    style = MaterialTheme.typography.labelMedium,
                    color = NocturnePalette.Ash
                )
                Spacer(Modifier.weight(1f))
                if (state.playlists.isNotEmpty()) {
                    Text(
                        text = "${state.playlists.size} source(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = NocturnePalette.Ash
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    EmptyState(
                        title = when (HomeTab.entries[tab]) {
                            HomeTab.VAULT -> "Nothing buried here yet"
                            HomeTab.RECENT -> "No restless spirits watched"
                            else -> "No matches"
                        },
                        message = when (HomeTab.entries[tab]) {
                            HomeTab.VAULT -> "Tap the star on any channel to keep it in the vault."
                            HomeTab.RECENT -> "Channels you watch will haunt this list."
                            else -> "Try a different name or group."
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            isFavorite = favorites.contains(channel.id),
                            currentProgram = EpgLookup.current(state.epg, channel),
                            onClick = { onPlay(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTab(
    state: AppState,
    onPlay: (Channel) -> Unit,
    onOpenGuide: () -> Unit
) {
    val live = remember(state.channels, state.epg) {
        state.channels.filter { EpgLookup.current(state.epg, it) != null }
    }

    if (state.epg.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EmptyState(
                title = "No guide in the walls",
                message = "Load an XMLTV guide to see what's playing right now.",
                action = {
                    ExtendedFloatingActionButton(
                        onClick = onOpenGuide,
                        containerColor = NocturnePalette.Blood,
                        contentColor = NocturnePalette.Bone,
                        icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                        text = { Text("Load guide") }
                    )
                }
            )
        }
        return
    }

    if (live.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            EmptyState(
                title = "Silence",
                message = "No programmes are airing right now for this guide."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(live, key = { it.id }) { channel ->
            ChannelRow(
                channel = channel,
                isFavorite = state.favorites.contains(channel.id),
                currentProgram = EpgLookup.current(state.epg, channel),
                onClick = { onPlay(channel) },
                onToggleFavorite = {}
            )
        }
    }
}