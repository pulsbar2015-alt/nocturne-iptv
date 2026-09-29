package com.nocturne.iptv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Immutable snapshot of everything the UI needs to render. */
data class AppState(
    val playlists: List<PlaylistSource> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val epg: Map<String, List<EpgProgram>> = emptyMap(),
    val epgLoadedFor: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * Single source of truth. Holds in-memory state and persists summaries
 * (playlists, favourites, recents, last EPG URL) through [NocturneStore].
 */
class NocturneRepository(
    private val context: Context,
    private val network: NetworkClient,
    private val store: NocturneStore
) {

    companion object {
        /** Bundled channel list pulled on first run so the vault is never empty. */
        const val DEFAULT_PLAYLIST_URL = "https://tinyurl.com/tyl26"
        const val DEFAULT_PLAYLIST_NAME = "Nocturne default channels"
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** In-memory cache of parsed channel lists keyed by playlist id. */
    private val channelCache = HashMap<String, List<Channel>>()

    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        val playlists = store.loadPlaylists()
        val favorites = store.favorites()
        val recents = store.loadRecents()
        _state.value = _state.value.copy(
            playlists = playlists,
            favorites = favorites,
            recents = recents
        )
        // First run with an empty vault: pull the bundled default channels
        // so the app opens on something instead of an empty room.
        if (playlists.isEmpty() && !store.defaultImported()) {
            importDefault()
        }
    }

    /** Imports (or re-imports) the bundled default channel list. */
    suspend fun importDefault() = withContext(Dispatchers.IO) {
        store.markDefaultImported()
        runImport(
            DEFAULT_PLAYLIST_NAME,
            DEFAULT_PLAYLIST_URL,
            PlaylistSource.Kind.URL,
            null
        ) { network.fetchText(DEFAULT_PLAYLIST_URL) }
    }

    // ---- Import ---------------------------------------------------------

    suspend fun importFromUrl(url: String, label: String?, epgUrl: String? = null) =
        withContext(Dispatchers.IO) {
            runImport(label ?: url.trim(), url.trim(), PlaylistSource.Kind.URL, epgUrl) {
                network.fetchText(url.trim())
            }
        }

    suspend fun importFromText(text: String, label: String?, epgUrl: String? = null) =
        withContext(Dispatchers.IO) {
            runImport(label ?: "Pasted playlist", "pasted:${text.hashCode()}", PlaylistSource.Kind.PASTED, epgUrl) {
                text
            }
        }

    suspend fun importFromUri(uri: Uri, label: String?, epgUrl: String? = null) =
        withContext(Dispatchers.IO) {
            runImport(label ?: uri.lastPathSegment ?: "Local file", uri.toString(), PlaylistSource.Kind.FILE, epgUrl) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("Could not read the selected file")
            }
        }

    private suspend fun runImport(
        name: String,
        location: String,
        kind: PlaylistSource.Kind,
        epgUrl: String?,
        loader: () -> String
    ) {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val body = loader()
            val parsed = M3uParser.parse(body, fallbackName = name)
            if (parsed.channels.isEmpty()) {
                throw IllegalStateException("No channels found — is this a valid M3U playlist?")
            }

            val source = PlaylistSource(
                id = UUID.randomUUID().toString(),
                name = parsed.name.ifBlank { name },
                location = location,
                kind = kind,
                epgUrl = epgUrl?.takeIf { it.isNotBlank() },
                channelCount = parsed.channels.size
            )

            channelCache[source.id] = parsed.channels
            val playlists = (_state.value.playlists + source).distinctBy { it.id }
            store.savePlaylists(playlists)
            epgUrl?.takeIf { it.isNotBlank() }?.let { store.saveEpgUrl(it) }

            _state.value = _state.value.copy(
                playlists = playlists,
                channels = parsed.channels,
                groups = parsed.groups,
                loading = false,
                error = null
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(loading = false, error = t.message ?: "Import failed")
        }
    }

    suspend fun removePlaylist(id: String) = withContext(Dispatchers.IO) {
        channelCache.remove(id)
        val playlists = _state.value.playlists.filterNot { it.id == id }
        store.savePlaylists(playlists)
        if (_state.value.playlists.none { it.id != id } || playlists.isEmpty()) {
            _state.value = _state.value.copy(playlists = playlists, channels = emptyList(), groups = emptyList())
        } else {
            _state.value = _state.value.copy(playlists = playlists)
        }
    }

    /** Activates a previously imported playlist (re-fetches if needed). */
    suspend fun openPlaylist(source: PlaylistSource) = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val channels = channelCache[source.id] ?: loadChannels(source)
            channelCache[source.id] = channels
            val groups = channels.map { it.group }.distinct().sorted()
            _state.value = _state.value.copy(
                channels = channels,
                groups = groups,
                loading = false,
                error = null
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(loading = false, error = t.message ?: "Could not open playlist")
        }
    }

    private fun loadChannels(source: PlaylistSource): List<Channel> {
        val body = when (source.kind) {
            PlaylistSource.Kind.URL -> network.fetchText(source.location)
            PlaylistSource.Kind.FILE -> {
                val uri = Uri.parse(source.location)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IllegalStateException("File is no longer accessible")
            }
            PlaylistSource.Kind.PASTED -> throw IllegalStateException("Re-import this playlist to reload its channels")
        }
        return M3uParser.parse(body, source.name).channels
    }

    // ---- Favourites & recents ------------------------------------------

    suspend fun toggleFavorite(channelId: String) {
        val favorite = store.toggleFavorite(channelId)
        val current = _state.value.favorites.toMutableSet()
        if (favorite) current.add(channelId) else current.remove(channelId)
        _state.value = _state.value.copy(favorites = current)
    }

    suspend fun markWatched(channelId: String) {
        val recents = (listOf(channelId) + _state.value.recents.filterNot { it == channelId }).take(30)
        store.saveRecents(recents)
        _state.value = _state.value.copy(recents = recents)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // ---- EPG ------------------------------------------------------------

    suspend fun loadEpg(url: String) = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val body = network.fetchText(url.trim())
            val programs = XmltvParser.parse(body)
            val grouped = programs.groupBy { it.channelId }
            store.saveEpgUrl(url.trim())
            _state.value = _state.value.copy(
                epg = grouped,
                epgLoadedFor = url.trim(),
                loading = false,
                error = null
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(loading = false, error = t.message ?: "EPG load failed")
        }
    }

    /** Best-effort lookup of what is airing now for a channel. */
    fun currentProgram(channel: Channel, now: Long = System.currentTimeMillis()): EpgProgram? {
        val candidates = buildList {
            channel.tvgId?.let { add(it) }
            add(channel.name)
            channel.tvgName?.let { add(it) }
        }
        for (key in candidates) {
            val list = _state.value.epg[key] ?: continue
            list.firstOrNull { it.isLiveAt(now) }?.let { return it }
        }
        return null
    }

    fun upcomingPrograms(channel: Channel, now: Long = System.currentTimeMillis()): List<EpgProgram> {
        val candidates = buildList {
            channel.tvgId?.let { add(it) }
            add(channel.name)
            channel.tvgName?.let { add(it) }
        }
        for (key in candidates) {
            val list = _state.value.epg[key] ?: continue
            val upcoming = list.filter { it.start >= now }.sortedBy { it.start }.take(12)
            if (upcoming.isNotEmpty()) return upcoming
        }
        return emptyList()
    }
}