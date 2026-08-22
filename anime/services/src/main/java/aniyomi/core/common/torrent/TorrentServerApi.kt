package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import aniyomi.core.common.torrent.model.TorrentRequest
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

class TorrentServerApi(
    private val network: NetworkHelper,
    private val json: Json,
    private val preferences: TorrentPreferences,
) {
    val hostUrl
        get() = "http://127.0.0.1:$port"

    @Volatile
    private var port: Int = 0

    fun setPort(value: Int) {
        port = value
    }

    fun getPort(): Int {
        return port
    }

    suspend fun echo(): String {
        return try {
            network.client.newCall(GET("$hostUrl/echo")).awaitSuccess().body.string()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Error sending echo" }
            ""
        }
    }

    /**
     * Re-tune the server for watching a film on a phone.
     *
     * TorrServer's defaults are written for an always-on box on a home network, and three of them
     * are what a torrent on a phone feels like:
     *
     * - **64 MB of cache.** At the bitrate of a 1080p release that is about a minute of video. One
     *   slow stretch of swarm empties it and the picture stops, which is the "there is no buffering
     *   at all" of it.
     * - **25 connections.** A public swarm has hundreds of peers, and most of any given twenty-five
     *   are slow or are not seeding the pieces wanted next. Reaching a watchable rate takes minutes
     *   at that limit and seconds at a sane one.
     * - **Half the cache preloaded before the first frame.** With a bigger cache that default gets
     *   *worse*: half of 192 MB is 96 MB to fetch before anything plays. The two have to move
     *   together, so the buffer grows while the wait shrinks.
     *
     * Read-modify-write rather than a blind set, because `action: "set"` replaces the whole record
     * — posting only these fields would reset the search hosts, the certificates and everything
     * else the server keeps beside them. Anything not named here keeps what it had.
     *
     * Failure is logged and nothing else. The server then plays on its own defaults: worse, but a
     * dialog about cache percentages is not what somebody who pressed play is owed.
     */
    suspend fun tuneForStreaming() {
        try {
            val current = network.client
                .newCall(POST("$hostUrl/settings", body = GET_SETTINGS.toRequestBody(jsonMime)))
                .awaitSuccess()
                .use { json.parseToJsonElement(it.body.string()).jsonObject }

            val body = buildJsonObject {
                put("action", "set")
                putJsonObject("sets") {
                    current.forEach { (key, value) -> if (key !in TUNED_KEYS) put(key, value) }
                    put("CacheSize", CACHE_BYTES)
                    put("PreloadCache", PRELOAD_PERCENT)
                    put("ReaderReadAHead", READ_AHEAD_PERCENT)
                    put("ConnectionsLimit", CONNECTIONS)
                    /*
                     * Two settings this server may or may not have, depending on its version.
                     *
                     * Written only when the record we just read already carries the key. A `set`
                     * replaces the whole record and the server parses it into a fixed struct, so
                     * inventing a field is silently ignored rather than harmful — but "silently
                     * ignored" is exactly how a setting comes to look implemented while doing
                     * nothing, and asking the record what it holds costs nothing and cannot lie.
                     */
                    if (DISABLE_UPLOAD in current) {
                        put(DISABLE_UPLOAD, !preferences.torrServerUpload().get())
                    }
                    if (DHT_LIMIT in current) {
                        put(DHT_LIMIT, DHT_CONNECTIONS)
                    }
                    // The tracker list this app installs is only consulted in mode 1. A server that
                    // came up in mode 0 — an older install, a settings file somebody edited — would
                    // ignore every one of them and fall back to whatever the magnet itself named.
                    put("RetrackersMode", RETRACKERS_ADD)
                    // Serve pieces as they arrive rather than waiting for each to verify whole. It
                    // is the default, and it is the difference between a stutter and a stall, so it
                    // is worth insisting on rather than inheriting.
                    put("ResponsiveMode", true)
                }
            }

            network.client
                .newCall(POST("$hostUrl/settings", body = body.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .close()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not tune the torrent server; its own defaults stand" }
        }
    }

    // / Torrents
    suspend fun addTorrent(
        link: String,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean,
    ): Torrent {
        val req = json.encodeToString(
            TorrentRequest(
                "add",
                link = link,
                title = title,
                poster = poster,
                data = data,
                saveToDb = save,
            ),
        )
        val resp = network.client.newCall(
            POST(
                "$hostUrl/torrents",
                body = req.toRequestBody("application/json".toMediaTypeOrNull()),
            ),
        ).awaitSuccess()
        return resp.use { json.decodeFromStream<Torrent>(it.body.byteStream()) }
    }

    /**
     * How one torrent is doing, or nothing if the server will not say.
     *
     * Polled once a second while the player waits, so every failure here is silent: a status read
     * that misses is one missing update on a screen that is about to get another, and logging each
     * one would fill the log with the ordinary case of a server that is busy fetching video.
     */
    suspend fun status(hash: String): Torrent? {
        if (hash.isBlank()) return null
        return try {
            val req = json.encodeToString(TorrentRequest("get", hash = hash))
            network.client.newCall(
                POST("$hostUrl/torrents", body = req.toRequestBody(jsonMime)),
            ).awaitSuccess().use { json.decodeFromStream<Torrent>(it.body.byteStream()) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun uploadTorrent(
        file: InputStream,
        title: String,
        save: Boolean = false,
    ): Torrent {
        val bytes = file.use { it.readBytes() }
        val fileRequestBody = bytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull())

        val requestBody = MultipartBody.Builder().apply {
            setType(MultipartBody.FORM)
            addFormDataPart("file", title, fileRequestBody)
            addFormDataPart("save", save.toString())
            addFormDataPart("title", title)
        }.build()

        val resp = network.client.newCall(
            POST(
                "$hostUrl/torrent/upload",
                body = requestBody,
            ),
        ).awaitSuccess()

        return resp.use { json.decodeFromStream<Torrent>(it.body.byteStream()) }
    }

    private companion object {
        val jsonMime = "application/json".toMediaTypeOrNull()
        const val GET_SETTINGS = """{"action":"get"}"""

        /** 192 MB, three times the default: three minutes of a 1080p release instead of one. */
        const val CACHE_BYTES = 192L * 1024 * 1024

        /**
         * How much of the cache to fill before the first frame, as a percent.
         *
         * Five of 192 MB is about 10 MB. It was fifteen, which is about 29 MB, and that number was
         * chosen against the wrong case: on a healthy torrent 29 MB arrives in seconds and nobody
         * notices either figure. The case that matters is the thin swarm, where 29 MB at the rate
         * two seeders can manage is minutes of spinner — reported from a device as the app getting
         * stuck rather than as a torrent being slow.
         *
         * Starting on 10 MB does not make the download faster. It makes the *wait* shorter, and
         * lets the rest of the buffer fill behind a picture instead of in front of one. The cost is
         * that a swarm barely keeping up with the bitrate will now stutter where it used to make
         * you wait — and a video that plays and stutters is worth more than a spinner that does
         * not, which is the whole trade.
         */
        const val PRELOAD_PERCENT = 5

        /** The server's own default, restated because everything around it is being changed. */
        const val READ_AHEAD_PERCENT = 95

        const val CONNECTIONS = 120
        const val RETRACKERS_ADD = 1

        /**
         * How many peers to look for through the DHT at once.
         *
         * Finding peers and downloading from them are separate budgets, and on a thin torrent the
         * first is the one that is short. Raising it is the closest thing there is to "look harder
         * while you wait" — the swarm is searched more widely in parallel with whatever is already
         * arriving, rather than the app sitting on the few peers it found first.
         */
        const val DHT_CONNECTIONS = 500

        const val DISABLE_UPLOAD = "DisableUpload"
        const val DHT_LIMIT = "DhtConnectionLimit"

        /** Written unconditionally, so they are the keys not to copy from the old record. */
        val TUNED_KEYS = setOf(
            "CacheSize",
            "PreloadCache",
            "ReaderReadAHead",
            "ConnectionsLimit",
            "RetrackersMode",
            "ResponsiveMode",
            DISABLE_UPLOAD,
            DHT_LIMIT,
        )
    }
}
