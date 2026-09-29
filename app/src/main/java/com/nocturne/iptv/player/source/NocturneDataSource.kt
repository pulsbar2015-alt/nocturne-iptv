package com.nocturne.iptv.player.source

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.rtmp.RtmpDataSource
import com.nocturne.iptv.data.NetworkClient

/**
 * One [DataSource.Factory] for every transport Nocturne speaks.
 *
 * Routing, by URI scheme:
 * - `http/https/file/content/asset` → stock ExoPlayer ([DefaultDataSource]).
 *   This is the HLS/DASH/TS/MP4 path — ExoPlayer does the heavy lifting.
 * - `rtmp/rtmps/…` → the media3 RTMP extension ([RtmpDataSource]). Real playback.
 * - `tvbus/mitv/p8p/vjms` → [NativeBridges]; a real vendor bridge plays them,
 *   otherwise the placeholder fails fast with a clear message.
 * - anything else → stock ExoPlayer, which sniffs the container.
 *
 * therefore the rest of the app never branches on scheme: it always sets a
 * media item and plays.
 */
@OptIn(UnstableApi::class)
class NocturneDataSourceFactory(
    context: Context,
    bridges: Map<String, ProtocolBridge> = NativeBridges.byScheme
) : DataSource.Factory {

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(NetworkClient.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    private val defaultFactory = DefaultDataSource.Factory(context, httpFactory)
    private val rtmpFactory = RtmpDataSource.Factory()
    private val bridgeMap = bridges

    override fun createDataSource(): DataSource =
        NocturneDataSource(
            default = defaultFactory.createDataSource(),
            rtmp = rtmpFactory.createDataSource(),
            bridges = bridgeMap
        )
}

@OptIn(UnstableApi::class)
private class NocturneDataSource(
    private val default: DataSource,
    private val rtmp: DataSource,
    private val bridges: Map<String, ProtocolBridge>
) : DataSource {

    private var delegate: DataSource? = null
    private val listeners = ArrayList<TransferListener>()

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase().orEmpty()
        val selected = when {
            scheme in Transports.RTMP_SCHEMES -> rtmp
            scheme in bridges -> bridges.getValue(scheme).createDataSource()
            else -> default
        }
        delegate = selected
        listeners.forEach { selected.addTransferListener(it) }
        return selected.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        requireNotNull(delegate) { "DataSource.open() must be called before read()" }
            .read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri

    override fun addTransferListener(transferListener: TransferListener) {
        if (listeners.contains(transferListener)) return
        listeners.add(transferListener)
        delegate?.addTransferListener(transferListener)
    }

    override fun close() {
        delegate?.close()
        delegate = null
    }
}
