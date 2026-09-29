package com.nocturne.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val sampleM3u =
        """#EXTM3U
          |#EXTINF:-1 tvg-id="bbc1" tvg-name="BBC One" group-title="UK",BBC One
          |http://example.com/bbc.ts
          |#EXTINF:-1 tvg-id="sky" tvg-name="Sky Sports" group-title="Sports",Sky Sports
          |http://example.com/sky.ts
          |#EXTINF:-1 tvg-id="cnn" tvg-name="CNN" group-title="News",CNN
          |http://example.com/cnn.ts
          |#EXTINF tvg-id="ignored" tvg-name="No Group", group-title="Movies",Movie X
          |http://example.com/movie.ts
          |#EXTGRP:New Group
          |#EOF
          |""".trimMargin()

    @Test
    fun `parse returns channels with correct ids names and groups`() {
        val parsed = M3uParser.parse(sampleM3u, fallbackName = "MyPlaylist")

        assertEquals("MyPlaylist", parsed.name)

        assertEquals(4, parsed.channels.size)

        val bbc = parsed.channels.find { it.name == "BBC One" }
        assertNotNull(bbc)
        assertEquals("bbc1", bbc?.tvgId)
        assertEquals("BBC One", bbc?.tvgName)
        assertEquals("UK", bbc?.group)

        val sky = parsed.channels.find { it.name == "Sky Sports" }
        assertNotNull(sky)
        assertEquals("sky", sky?.tvgId)
        assertEquals("Sky Sports", sky?.tvgName)
        assertEquals("Sports", sky?.group)

        val cnn = parsed.channels.find { it.name == "CNN" }
        assertNotNull(cnn)
        assertEquals("cnn", cnn?.tvgId)
        assertEquals("CNN", cnn?.tvgName)
        assertEquals("News", cnn?.group)

        // the last entry keeps its stream URL in the id
        val movie = parsed.channels.find { it.name == "Movie X" }
        assertNotNull(movie)
        assertTrue(movie?.id?.endsWith("http://example.com/movie.ts") == true)
    }

    @Test
    fun `parse handles empty body with fallback name`() {
        val parsed = M3uParser.parse("", fallbackName = "Empty")
        assertEquals("Empty", parsed.name)
        assertEquals(0, parsed.channels.size)
    }

    @Test
    fun `parse respects #EXTGRP directive`() {
        val body =
            """#EXTM3U
              |#EXTGRP:New Group
              |#EXTINF:-1 tvg-name="G",G
              |http://u.ts
              |""".trimMargin()
        val parsed = M3uParser.parse(body, fallbackName = "test")
        assertEquals("test", parsed.name)
        assertEquals(listOf("New Group"), parsed.groups)
        assertEquals("New Group", parsed.channels.single().group)
    }

    @Test
    fun `parse merges duplicate channels into alternate sources`() {
        val body =
            """#EXTM3U
              |#EXTINF:-1 tvg-id="bbc1" tvg-logo="http://example.com/bbc.png" group-title="UK",BBC One
              |http://server-a.example.com/bbc.m3u8
              |#EXTINF:-1 tvg-id="bbc1" group-title="UK",BBC One
              |http://server-b.example.com/bbc.m3u8
              |#EXTINF:-1 tvg-id="bbc1" group-title="UK",BBC One
              |http://server-b.example.com/bbc.m3u8
              |#EXTINF:-1 tvg-id="cnn" group-title="News",CNN
              |http://server-a.example.com/cnn.m3u8
              |""".trimMargin()

        val parsed = M3uParser.parse(body, fallbackName = "dupes")

        assertEquals(2, parsed.channels.size)

        val bbc = parsed.channels.find { it.tvgId == "bbc1" }
        assertNotNull(bbc)
        assertEquals("http://server-a.example.com/bbc.m3u8", bbc?.url)
        // First entry's metadata wins; the repeated identical URL is dropped.
        assertEquals("http://example.com/bbc.png", bbc?.logo)
        assertEquals(
            listOf("http://server-b.example.com/bbc.m3u8"),
            bbc?.sources
        )
        assertEquals(
            listOf(
                "http://server-a.example.com/bbc.m3u8",
                "http://server-b.example.com/bbc.m3u8"
            ),
            bbc?.allSources
        )

        val cnn = parsed.channels.find { it.tvgId == "cnn" }
        assertNotNull(cnn)
        assertTrue(cnn?.sources?.isEmpty() == true)
    }

    @Test
    fun `parse merges same-name channels within one group`() {
        val body =
            """#EXTM3U
              |#EXTINF:-1 group-title="Sports",Match TV
              |rtmp://a.example.com/live/match
              |#EXTINF:-1 group-title="Sports",Match TV
              |http://b.example.com/match.m3u8
              |""".trimMargin()

        val parsed = M3uParser.parse(body, fallbackName = "namemerge")

        assertEquals(1, parsed.channels.size)
        val match = parsed.channels.single()
        assertEquals("rtmp://a.example.com/live/match", match.url)
        assertEquals(listOf("http://b.example.com/match.m3u8"), match.sources)
    }
}
