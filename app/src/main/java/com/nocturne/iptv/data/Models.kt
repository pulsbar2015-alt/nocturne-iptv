package com.nocturne.iptv.data

/** A single playable channel/track parsed from an M3U playlist. */
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = UNGROUPED,
    val tvgId: String? = null,
    val tvgName: String? = null
) {
    companion object {
        const val UNGROUPED = "Unsorted"
    }
}

/** Metadata about an imported playlist source. */
data class PlaylistSource(
    val id: String,
    val name: String,
    val location: String,
    val kind: Kind,
    val epgUrl: String? = null,
    val channelCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis()
) {
    enum class Kind { URL, FILE, PASTED }
}

/** One program entry from an XMLTV EPG feed. */
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Long,
    val stop: Long
) {
    fun isLiveAt(now: Long): Boolean = now in start until stop
    val durationMinutes: Long get() = ((stop - start) / 60_000L).coerceAtLeast(0)
}

/** Result of parsing a playlist body. */
data class ParsedPlaylist(
    val name: String,
    val channels: List<Channel>,
    val groups: List<String>
)