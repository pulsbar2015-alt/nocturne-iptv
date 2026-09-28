package com.nocturne.iptv.player

import com.nocturne.iptv.data.Channel

/**
 * Cross-activity hand-off for the currently playing queue.
 *
 * Held on the [com.nocturne.iptv.NocturneApp] application object so both the
 * browsing UI and [PlayerActivity] see the same list without serialising large
 * playlists through an Intent.
 */
class PlaybackSession {
    var channels: List<Channel> = emptyList()
        private set

    var index: Int = 0
        private set

    val current: Channel? get() = channels.getOrNull(index)

    fun start(channels: List<Channel>, index: Int) {
        this.channels = channels
        this.index = index.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    }

    fun move(delta: Int) {
        if (channels.isEmpty()) return
        index = (index + delta + channels.size) % channels.size
    }

    fun hasNext(): Boolean = channels.size > 1
}