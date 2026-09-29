package com.nocturne.iptv.player.source

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Names the transport behind a stream URL the way a channel guide would, and
 * builds the [MediaItem] that lets ExoPlayer pick the right container.
 *
 * Everything stock ExoPlayer already understands (HLS/DASH/TS/MP4 over
 * http/https) stays on the native path; `rtmp…` goes to the media3 RTMP
 * extension; `tvbus`/`mitv`/`p8p`/`vjms` are handed to [NativeBridges].
 */
@OptIn(UnstableApi::class)
object Transports {

    /** URI schemes that need a vendor bridge instead of stock ExoPlayer. */
    val BRIDGED_SCHEMES = setOf("tvbus", "mitv", "p8p", "vjms")

    val RTMP_SCHEMES = setOf("rtmp", "rtmps", "rtmpt", "rtmpe", "rtmpte")

    fun schemeOf(url: String): String? =
        runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase()

    private fun pathTail(url: String): String =
        runCatching { Uri.parse(url).lastPathSegment.orEmpty() }.getOrDefault("").lowercase()

    /** Short human label: HLS, DASH, TS, MP4, RTMP, TVBUS… */
    fun label(url: String): String {
        val scheme = schemeOf(url) ?: return "STREAM"
        val tail = pathTail(url)
        return when {
            scheme in RTMP_SCHEMES -> "RTMP"
            scheme in BRIDGED_SCHEMES -> scheme.uppercase()
            tail.endsWith(".m3u8") || tail.endsWith(".m3u") -> "HLS"
            tail.endsWith(".mpd") -> "DASH"
            tail.endsWith(".ts") -> "TS"
            tail.endsWith(".mp4") || tail.endsWith(".mkv") || tail.endsWith(".webm") -> "MP4"
            else -> "STREAM"
        }
    }

    /** Builds the media item, hinting the container when the URL makes it obvious. */
    fun mediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        // Pinning HLS/DASH by extension saves ExoPlayer from sniffing a dead guess.
        val tail = pathTail(url)
        when {
            tail.endsWith(".m3u8") || tail.endsWith(".m3u") ->
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            tail.endsWith(".mpd") ->
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        return builder.build()
    }
}
