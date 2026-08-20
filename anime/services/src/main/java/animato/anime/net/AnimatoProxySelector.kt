package animato.anime.net

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * The app's proxy, installed where every request will find it.
 *
 * ## Why the JVM default and not the OkHttp builder
 *
 * The obvious place is `OkHttpClient.Builder.proxy`, and it is not available: the one client this
 * app makes requests with is built in `NetworkHelper`, which is Mihon's file and is not ours to
 * edit. Reaching around that with a second client would fix our own requests and leave every
 * extension — which is to say almost every request — going direct.
 *
 * OkHttp resolves its proxy as `builder.proxySelector ?: ProxySelector.getDefault()`, and Mihon's
 * builder sets neither a proxy nor a selector. So installing a default selector reaches the same
 * client, every extension's client, the image loader, and anything else in the process that speaks
 * HTTP through Java — without a line changing upstream.
 *
 * ## Why a live selector rather than setting a proxy once
 *
 * OkHttp reads the default *once*, when the client is constructed, and that client outlives every
 * visit to the settings screen. A selector object read once but *asked* on every connection turns
 * that into a setting that takes effect immediately: this is the object that got captured, and it
 * looks the answer up each time it is called.
 *
 * ## What it does not cover
 *
 * mpv, which does its own networking through ffmpeg rather than through Java, and is told about
 * the proxy separately where its options are set. The torrent server likewise has its own proxy
 * setting, which was already there. Both are stated on the settings screen rather than left for
 * somebody to discover.
 */
class AnimatoProxySelector(
    /**
     * Whatever was the default before this replaced it.
     *
     * Delegated to whenever our proxy is off, rather than answering "go direct". A device with a
     * system or carrier proxy configured has one of these, and a switch that is *off* must leave
     * that alone — turning our proxy off should restore what the phone was doing, not override it
     * in the opposite direction.
     */
    private val fallback: ProxySelector?,
) : ProxySelector() {

    override fun select(uri: URI?): List<Proxy> {
        val proxy = runCatching { Injekt.get<ProxyPreferences>().proxy() }
            // Reached before the graph is built — a request during start-up — which is a reason to
            // go by the system's answer rather than to bring the request down.
            .getOrElse {
                logcat(LogPriority.DEBUG, it) { "Proxy preferences not ready; deferring to the system" }
                null
            }
            ?: return fallback?.select(uri) ?: listOf(Proxy.NO_PROXY)
        return listOf(proxy)
    }

    /**
     * A proxy that could not be reached, reported.
     *
     * Logged rather than acted on. A selector is allowed to respond by offering a different proxy
     * next time, and doing that here would mean silently sending traffic somewhere the user did
     * not choose — which for the one feature whose entire purpose is *where the traffic comes
     * from* is the worst thing this class could do. The request fails, and it fails visibly.
     */
    override fun connectFailed(uri: URI?, socketAddress: SocketAddress?, exception: IOException?) {
        logcat(LogPriority.WARN, exception) { "Proxy could not be reached: $socketAddress" }
        fallback?.connectFailed(uri, socketAddress, exception)
    }

    companion object {

        /**
         * Take over the process's proxy resolution.
         *
         * Called during application bind, before anything has built an HTTP client — which is the
         * whole requirement, since a client built earlier would have captured the previous default
         * and would keep using it for the life of the process.
         *
         * Nothing is read here. The preference store is not available this early, and this object
         * is written so that it does not need to be: the first `select` happens on the first
         * request, by which time the graph has been built for a long time.
         */
        fun install() {
            if (getDefault() is AnimatoProxySelector) return
            setDefault(AnimatoProxySelector(getDefault()))
            Authenticator.setDefault(ProxyAuthenticator)
        }
    }

    /**
     * The username and password, for the proxy and for nothing else.
     *
     * SOCKS5 authentication happens inside the socket implementation, below OkHttp, and the only
     * hook it offers is the JVM's global authenticator. That hook is global in the literal sense —
     * every server that answers with a 401 reaches it too — so this answers for the proxy alone.
     *
     * The check is the requestor type, not the address: a proxy is asked for credentials as
     * [Authenticator.RequestorType.PROXY], and anything else asking is a site that wants a
     * password, which this app does not have and must not guess at.
     */
    private object ProxyAuthenticator : Authenticator() {
        override fun getPasswordAuthentication(): PasswordAuthentication? {
            if (requestorType != RequestorType.PROXY) return null
            val preferences = runCatching { Injekt.get<ProxyPreferences>() }.getOrNull() ?: return null
            val (user, secret) = preferences.credentials() ?: return null
            // Only for the proxy that is actually configured. Another proxy on the path asking for
            // a password is not ours to answer, and handing these to it would send the credentials
            // somewhere the user never named.
            val configured = preferences.proxy()?.address() as? InetSocketAddress ?: return null
            if (!requestingHost.equals(configured.hostString, ignoreCase = true)) return null
            if (requestingPort != configured.port) return null
            return PasswordAuthentication(user, secret.toCharArray())
        }
    }
}
