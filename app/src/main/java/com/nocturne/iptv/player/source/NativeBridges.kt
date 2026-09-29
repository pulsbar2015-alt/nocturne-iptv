package com.nocturne.iptv.player.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.nocturne.iptv.data.NetworkClient
import java.io.IOException

/**
 * Bridges for proprietary IPTV transports (`tvbus://`, `mitv://`, `p8p://`,
 * `vjms://`).
 *
 * These schemes are owned by their vendors and are NOT directly playable by
 * stock ExoPlayer. A bridge converts such a URI into something ExoPlayer can
 * consume — in practice that means rewriting it to the local HTTP proxy the
 * vendor's SDK serves on-device (see [ProxyRewriteBridge]).
 *
 * Until a vendor SDK is bundled, each scheme is parked behind a
 * [MissingSdkBridge], which fails fast with a clear message instead of a
 * confusing generic error. When you have an SDK, drop it in and call
 * [NativeBridges.register] (or [NativeBridges.registerAll]) once at startup;
 * the registered bridge shadows the placeholder.
 */
@OptIn(UnstableApi::class)
interface ProtocolBridge {
    /** URI schemes this bridge owns, lowercase, without `://`. */
    val schemes: Set<String>

    /** Human label shown where the transport is named, e.g. "TvBus". */
    val label: String

    fun createDataSource(): DataSource
}

/**
 * Rewrites `scheme://rest` to `http://127.0.0.1:port/scheme/rest` and lets a
 * normal HTTP data source carry it — the exact shape every vendor SDK proxy
 * takes. Point [proxyPort] at the port your SDK serves and bridged streams
 * play without touching the player code.
 */
@OptIn(UnstableApi::class)
class ProxyRewriteBridge(
    override val schemes: Set<String>,
    override val label: String,
    val proxyHost: String = "127.0.0.1",
    val proxyPort: Int
) : ProtocolBridge {

    private val factory = DefaultHttpDataSource.Factory()
        .setUserAgent(NetworkClient.USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    override fun createDataSource(): DataSource = RewrittenDataSource()

    private fun rewrite(uri: Uri): Uri {
        val scheme = uri.scheme?.lowercase().orEmpty()
        val rest = uri.toString().substringAfter("$scheme://")
        return Uri.parse("http://$proxyHost:$proxyPort/$scheme/$rest")
    }

    private inner class RewrittenDataSource : DataSource {
        private var inner: DataSource? = null

        override fun open(dataSpec: DataSpec): Long {
            val rewritten = dataSpec.buildUpon().setUri(rewrite(dataSpec.uri)).build()
            val source = factory.createDataSource().also { inner = it }
            return source.open(rewritten)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            requireNotNull(inner) { "open() must be called before read()" }
                .read(buffer, offset, length)

        override fun getUri(): Uri? = inner?.uri

        override fun close() {
            inner?.close()
            inner = null
        }
    }
}

/** Placeholder bridge: explains exactly which SDK is missing for the scheme. */
@OptIn(UnstableApi::class)
class MissingSdkBridge(
    override val schemes: Set<String>,
    override val label: String,
    private val sdkName: String
) : ProtocolBridge {

    override fun createDataSource(): DataSource = MissingSdkDataSource()

    private inner class MissingSdkDataSource : DataSource {
        override fun open(dataSpec: DataSpec): Long {
            throw IOException(
                "${dataSpec.uri.scheme}:// streams need the $sdkName SDK. " +
                    "Bundle the SDK and register its bridge in NativeBridges " +
                    "(see the README's \"Proprietary transports\" section)."
            )
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            C.ERROR_END_OF_INPUT

        override fun getUri(): Uri? = null

        override fun close() = Unit
    }
}

/** Built-in placeholders plus anything registered at runtime. */
@OptIn(UnstableApi::class)
object NativeBridges {

    private val placeholders: List<ProtocolBridge> = listOf(
        MissingSdkBridge(setOf("tvbus"), "TvBus", "TvBus"),
        MissingSdkBridge(setOf("mitv"), "MiTV", "MiTV"),
        MissingSdkBridge(setOf("p8p"), "P8P", "P8P"),
        MissingSdkBridge(setOf("vjms"), "VJMS", "VJMS")
    )

    private val registered = mutableListOf<ProtocolBridge>()

    private val registeredSchemes: Set<String>
        get() = registered.flatMap { it.schemes }.toSet()

    /**
     * Registers a vendor bridge (call once at startup after bundling an SDK).
     * It shadows the built-in placeholder for every scheme it owns.
     */
    @Synchronized
    fun register(bridge: ProtocolBridge) {
        registered.removeAll { existing -> existing.schemes.any { it in bridge.schemes } }
        registered.add(bridge)
    }

    @Synchronized
    fun registerAll(bridges: Iterable<ProtocolBridge>) {
        bridges.forEach(::register)
    }

    @get:Synchronized
    val all: List<ProtocolBridge>
        get() = registered + placeholders.filterNot { placeholder ->
            placeholder.schemes.any { it in registeredSchemes }
        }

    /** scheme → owning bridge, consulted by [NocturneDataSourceFactory]. */
    @get:Synchronized
    val byScheme: Map<String, ProtocolBridge>
        get() = all.flatMap { bridge -> bridge.schemes.map { it to bridge } }.toMap()
}
