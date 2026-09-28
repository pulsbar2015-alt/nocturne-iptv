package com.nocturne.iptv

import android.app.Application
import com.nocturne.iptv.data.NetworkClient
import com.nocturne.iptv.data.NocturneRepository
import com.nocturne.iptv.data.NocturneStore
import com.nocturne.iptv.player.PlaybackSession

/** Manual DI container — small enough that a framework would be overkill. */
class NocturneApp : Application() {

    val network: NetworkClient by lazy { NetworkClient() }
    val store: NocturneStore by lazy { NocturneStore(this) }
    val repository: NocturneRepository by lazy { NocturneRepository(this, network, store) }
    val playbackSession: PlaybackSession by lazy { PlaybackSession() }
}