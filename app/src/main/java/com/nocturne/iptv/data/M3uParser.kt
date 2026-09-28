package com.nocturne.iptv.data

import java.util.Locale

/**
 * Parser for M3U / M3U8 playlists.
 *
 * Handles the extended M3U convention used by IPTV providers:
 *
 *   #EXTM3U
 *   #EXTINF:-1 tvg-id="bbc1" tvg-name="BBC One" tvg-logo="http://…" group-title="UK",BBC One
 *   http://host/stream.m3u8
 *
 * Playlists are frequently messy in the wild, so the parser is forgiving:
 * attribute quotes, order, and casing are all treated loosely.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")
    private val ATTR_REGEX_SINGLE = Regex("([A-Za-z0-9_-]+)\\s*=\\s*'([^']*)'")

    fun parse(body: String, fallbackName: String = "Playlist"): ParsedPlaylist {
        val lines = body.lineSequence().map { it.trim() }.toList()
        val channels = ArrayList<Channel>()
        val groups = LinkedHashSet<String>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var index = 0
        var playlistName = fallbackName

        for (raw in lines) {
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    attrs["name"]?.takeIf { it.isNotBlank() }?.let { playlistName = it }
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = parseAttributes(line)
                    pendingTvgId = attrs["tvg-id"]
                    pendingTvgName = attrs["tvg-name"]
                    pendingLogo = attrs["tvg-logo"] ?: attrs["logo"]
                    pendingGroup = attrs["group-title"] ?: attrs["group"]
                    pendingName = displayName(line)
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    if (pendingGroup.isNullOrBlank()) {
                        pendingGroup = line.substringAfter(':', "").trim().ifBlank { null }
                    }
                }

                line.startsWith("#") -> Unit // other directives ignored

                else -> {
                    // A non-comment line is a stream URL.
                    val url = line
                    val name = pendingName
                        ?: pendingTvgName
                        ?: url.substringAfterLast('/').substringBefore('?')
                            .ifBlank { "Channel ${index + 1}" }
                    val group = pendingGroup?.takeIf { it.isNotBlank() } ?: Channel.UNGROUPED

                    channels += Channel(
                        id = "$index-$url",
                        name = name,
                        url = url,
                        logo = pendingLogo?.takeIf { it.isNotBlank() },
                        group = group,
                        tvgId = pendingTvgId,
                        tvgName = pendingTvgName
                    )
                    groups += group
                    index++
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingTvgId = null
                    pendingTvgName = null
                }
            }
        }

        return ParsedPlaylist(
            name = playlistName,
            channels = channels,
            groups = groups.toList()
        )
    }

    /** Everything after the final comma of an #EXTINF line is the display title. */
    private fun displayName(line: String): String? {
        val comma = line.indexOf(',')
        if (comma == -1) return null
        return line.substring(comma + 1).trim().ifBlank { null }
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val out = HashMap<String, String>()
        ATTR_REGEX.findAll(line).forEach { out[it.groupValues[1].lowercase(Locale.ROOT)] = it.groupValues[2] }
        ATTR_REGEX_SINGLE.findAll(line).forEach { out[it.groupValues[1].lowercase(Locale.ROOT)] = it.groupValues[2] }
        return out
    }
}