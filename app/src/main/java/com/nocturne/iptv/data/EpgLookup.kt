package com.nocturne.iptv.data

/** Read-only EPG queries so composables can derive "now/next" from state. */
object EpgLookup {

    private fun keys(channel: Channel): List<String> = buildList {
        channel.tvgId?.takeIf { it.isNotBlank() }?.let { add(it) }
        channel.tvgName?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (channel.name.isNotBlank()) add(channel.name)
    }

    fun current(
        epg: Map<String, List<EpgProgram>>,
        channel: Channel,
        now: Long = System.currentTimeMillis()
    ): EpgProgram? {
        for (key in keys(channel)) {
            epg[key]?.firstOrNull { it.isLiveAt(now) }?.let { return it }
        }
        return null
    }

    fun next(
        epg: Map<String, List<EpgProgram>>,
        channel: Channel,
        now: Long = System.currentTimeMillis()
    ): EpgProgram? {
        for (key in keys(channel)) {
            epg[key]?.filter { it.start > now }?.minByOrNull { it.start }?.let { return it }
        }
        return null
    }

    fun upcoming(
        epg: Map<String, List<EpgProgram>>,
        channel: Channel,
        now: Long = System.currentTimeMillis(),
        limit: Int = 12
    ): List<EpgProgram> {
        for (key in keys(channel)) {
            val list = epg[key] ?: continue
            val upcoming = list.filter { it.start >= now }.sortedBy { it.start }.take(limit)
            if (upcoming.isNotEmpty()) return upcoming
        }
        return emptyList()
    }

    fun hasAny(epg: Map<String, List<EpgProgram>>, channel: Channel): Boolean =
        keys(channel).any { epg.containsKey(it) }
}