package com.nocturne.iptv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Streaming XMLTV parser.
 *
 * Reads `<programme channel="…" start="20240101120000 +0000" stop="…">` blocks and
 * extracts `<title>` and `<desc>`. Designed to stream rather than build a DOM so
 * large guides (tens of MB) do not exhaust memory.
 */
object XmltvParser {

    // XMLTV normally emits "... +0000" / "... -0500".
    private val OFFSET_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyyMMddHHmm Z", Locale.ROOT)
    )

    // Some feeds omit the zone entirely.
    private val LOCAL_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyyMMddHHmm", Locale.ROOT)
    )

    fun parse(body: String): List<EpgProgram> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(body))

        val programs = ArrayList<EpgProgram>()

        var channelId: String? = null
        var start: Long = 0
        var stop: Long = 0
        var insideTitle = false
        var insideDesc = false
        var title = ""
        var desc: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "programme" -> {
                            channelId = parser.getAttributeValue(null, "channel")
                            start = parseTime(parser.getAttributeValue(null, "start"))
                            stop = parseTime(parser.getAttributeValue(null, "stop"))
                            title = ""
                            desc = null
                        }
                        "title" -> insideTitle = true
                        "desc" -> insideDesc = true
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (insideTitle) title += text
                    if (insideDesc) desc = (desc ?: "") + text
                }

                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase(Locale.ROOT)) {
                        "title" -> insideTitle = false
                        "desc" -> insideDesc = false
                        "programme" -> {
                            if (channelId != null && stop > start) {
                                programs += EpgProgram(
                                    channelId = channelId,
                                    title = title.trim().ifBlank { "Untitled" },
                                    description = desc?.trim()?.ifBlank { null },
                                    start = start,
                                    stop = stop
                                )
                            }
                            channelId = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        return programs
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        val cleaned = value.trim()

        // Try the offset-aware form first — the ZoneOffset handles "+0000",
        // "+00:00" and "Z" uniformly.
        if (cleaned.contains(' ')) {
            for (fmt in OFFSET_FORMATTERS) {
                try {
                    return OffsetDateTime.parse(cleaned, fmt).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    // try next formatter
                }
            }
        }

        for (fmt in LOCAL_FORMATTERS) {
            try {
                return LocalDateTime.parse(cleaned, fmt)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                // try next formatter
            }
        }

        return 0
    }
}