package com.nocturne.iptv.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nocturne.iptv.NocturneApp
import com.nocturne.iptv.data.AppState
import com.nocturne.iptv.data.Channel
import com.nocturne.iptv.data.PlaylistSource
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NocturneViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as NocturneApp).repository

    val state: StateFlow<AppState> = repository.state

    init {
        viewModelScope.launch { repository.bootstrap() }
    }

    fun importFromUrl(url: String, label: String?, epgUrl: String?) {
        viewModelScope.launch { repository.importFromUrl(url, label, epgUrl) }
    }

    fun importFromText(text: String, label: String?, epgUrl: String?) {
        viewModelScope.launch { repository.importFromText(text, label, epgUrl) }
    }

    fun importFromUri(uri: Uri, label: String?, epgUrl: String?) {
        viewModelScope.launch { repository.importFromUri(uri, label, epgUrl) }
    }

    fun openPlaylist(source: PlaylistSource) {
        viewModelScope.launch { repository.openPlaylist(source) }
    }

    fun removePlaylist(id: String) {
        viewModelScope.launch { repository.removePlaylist(id) }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { repository.toggleFavorite(channel.id) }
    }

    fun markWatched(channel: Channel) {
        viewModelScope.launch { repository.markWatched(channel.id) }
    }

    fun loadEpg(url: String) {
        viewModelScope.launch { repository.loadEpg(url) }
    }

    fun currentProgram(channel: Channel) = repository.currentProgram(channel)

    fun upcomingPrograms(channel: Channel) = repository.upcomingPrograms(channel)

    fun clearError() {
        repository.clearError()
    }
}