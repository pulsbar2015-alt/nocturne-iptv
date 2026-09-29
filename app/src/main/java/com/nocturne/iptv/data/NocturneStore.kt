package com.nocturne.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "nocturne_store")

/**
 * Local persistence for playlists, favourites, recents and EPG URLs.
 * Backed by Preferences DataStore; complex records are stored as JSON strings.
 */
class NocturneStore(private val context: Context) {

    private object Keys {
        val PLAYLISTS = stringPreferencesKey("playlists")
        val FAVORITES = stringSetPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
        val EPG_URLS = stringPreferencesKey("epg_urls")
        val LAST_EPG = stringPreferencesKey("last_epg")
        val DEFAULT_IMPORTED = booleanPreferencesKey("default_imported")
    }

    // ---- Playlists ------------------------------------------------------

    suspend fun savePlaylists(playlists: List<PlaylistSource>) {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("location", p.location)
                    put("kind", p.kind.name)
                    put("epgUrl", p.epgUrl ?: JSONObject.NULL)
                    put("channelCount", p.channelCount)
                    put("importedAt", p.importedAt)
                }
            )
        }
        context.dataStore.edit { it[Keys.PLAYLISTS] = arr.toString() }
    }

    suspend fun loadPlaylists(): List<PlaylistSource> {
        val raw = context.dataStore.data.map { it[Keys.PLAYLISTS] }.first() ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlaylistSource(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    location = o.getString("location"),
                    kind = runCatching { PlaylistSource.Kind.valueOf(o.getString("kind")) }
                        .getOrDefault(PlaylistSource.Kind.URL),
                    epgUrl = o.optString("epgUrl").takeIf { it.isNotBlank() && it != "null" },
                    channelCount = o.optInt("channelCount"),
                    importedAt = o.optLong("importedAt", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    // ---- Favourites -----------------------------------------------------

    suspend fun favorites(): Set<String> =
        context.dataStore.data.map { it[Keys.FAVORITES] ?: emptySet() }.first()

    suspend fun toggleFavorite(channelId: String): Boolean {
        var nowFavorite = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES]?.toMutableSet() ?: mutableSetOf()
            nowFavorite = if (current.contains(channelId)) {
                current.remove(channelId); false
            } else {
                current.add(channelId); true
            }
            prefs[Keys.FAVORITES] = current
        }
        return nowFavorite
    }

    // ---- Recents --------------------------------------------------------

    suspend fun saveRecents(channelIds: List<String>) {
        val arr = JSONArray()
        channelIds.forEach { arr.put(it) }
        context.dataStore.edit { it[Keys.RECENTS] = arr.toString() }
    }

    suspend fun loadRecents(): List<String> {
        val raw = context.dataStore.data.map { it[Keys.RECENTS] }.first() ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    // ---- EPG ------------------------------------------------------------

    suspend fun saveEpgUrl(url: String) {
        context.dataStore.edit { it[Keys.LAST_EPG] = url }
    }

    suspend fun lastEpgUrl(): String? =
        context.dataStore.data.map { it[Keys.LAST_EPG] }.first()?.takeIf { it.isNotBlank() }

    // ---- Default playlist -------------------------------------------------

    /** True once the bundled default source has been pulled at least once. */
    suspend fun defaultImported(): Boolean =
        context.dataStore.data.map { it[Keys.DEFAULT_IMPORTED] ?: false }.first()

    suspend fun markDefaultImported() {
        context.dataStore.edit { it[Keys.DEFAULT_IMPORTED] = true }
    }
}