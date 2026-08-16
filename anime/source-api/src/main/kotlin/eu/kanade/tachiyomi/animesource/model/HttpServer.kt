package eu.kanade.tachiyomi.animesource.model

import fi.iki.elonen.NanoHTTPD
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * The local server an extension serves video through, so a player can be handed a plain URL.
 *
 * ## Why the hostname is written out
 *
 * This used to be `NanoHTTPD(0)`, and that constructor is `this(null, port)` — a null hostname,
 * which NanoHTTPD binds as `InetSocketAddress(port)`, the wildcard address. So the server listened
 * on **every interface**, Wi-Fi included, while every URL built from it says `localhost` and every
 * consumer of it is on this device: the external player, the downloader, mpv.
 *
 * The effect was that during external playback anything on the same network could read the stream,
 * unauthenticated. The port is ephemeral, which is obscurity rather than protection.
 *
 * Nothing wanted that, so the loopback address is now stated rather than left to a default. It is
 * not a signature change and extensions are unaffected — they subclass this and override `serve`.
 *
 * Casting will want the opposite, and should ask for it explicitly: a receiver on a television
 * cannot reach loopback. That belongs behind a deliberate opt-in with the exposure spelled out, not
 * behind a constructor default nobody read.
 */
open class HttpServer : NanoHTTPD(LOOPBACK, 0) {
    val url: String
        get() = "http://localhost:$listeningPort"

    fun isRunning(): Boolean {
        return isRunning
    }

    @Volatile
    private var isRunning = false

    override fun start() {
        try {
            super.start()
            isRunning = true
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG, e) { "Failed to start http server" }
        }
    }

    override fun stop() {
        super.stop()
        isRunning = false
    }

    companion object {
        const val PLACEHOLDER_URL = "http://localhost:1"

        /**
         * The literal address rather than the name, so the bind does not depend on how `localhost`
         * resolves. A name can answer with more than one address and a bind takes only one of them;
         * the URLs here say `localhost`, and the players that follow them — mpv, and whatever
         * external app someone chose — are the ones that would have to agree.
         */
        private const val LOOPBACK = "127.0.0.1"
    }
}
