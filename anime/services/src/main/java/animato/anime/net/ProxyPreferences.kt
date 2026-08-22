package animato.anime.net

import animato.anime.util.credentialString
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * What kind of proxy is on the other end.
 *
 * The two that matter, and the difference between them is not a detail. A SOCKS proxy carries the
 * TCP connection, so everything the app does goes through it and the far end sees the proxy's
 * address. An HTTP proxy is spoken to in HTTP, so it applies to HTTP requests and not to anything
 * else — which is fine here, because HTTP requests are what this app makes.
 */
enum class ProxyKind {
    Socks5,
    Http,
}

/**
 * One proxy for the whole app.
 *
 * ## Why this exists instead of a VPN
 *
 * The request was an in-app VPN. A real one means [android.net.VpnService], an implementation of
 * WireGuard or OpenVPN, and servers to connect to — a separate application's worth of work, and
 * one this project would have nothing to point at. What people want *from* it here is narrower and
 * is what this does: reach a source that is blocked, from an address that is not blocked, using a
 * provider they already pay for.
 *
 * ## Why the credentials are stored as they are
 *
 * In the ordinary preference store, in the clear, exactly like every extension login and tracker
 * token the app already holds. Not because that is ideal but because it is the truth: an app that
 * encrypted this one field would be claiming a protection it does not offer anywhere else.
 *
 * That reasoning originally ended *and a proxy password is no more sensitive than the MyAnimeList
 * token sitting beside it*, which was right about the comparison and wrong about the fact. The
 * tracker token is marked private, so it is the one thing a backup leaves out; this pair was not,
 * and so went into every backup in plain text. Being no more sensitive than the token is a reason
 * to treat it the same way, not a reason to treat it as public — see [credentialString].
 */
class ProxyPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val enabled = preferenceStore.getBoolean("animato_proxy_enabled", false)

    val kind = preferenceStore.getEnum("animato_proxy_kind", ProxyKind.Socks5)

    val host = preferenceStore.getString("animato_proxy_host", "")

    /**
     * Text rather than a number, because it is typed rather than chosen.
     *
     * The settings screen offers it as a field, an empty field is a real and ordinary state, and
     * an Int preference has no way to say *nothing typed yet* that is not also a port number.
     */
    val port = preferenceStore.getString("animato_proxy_port", "")

    // The pair, not just the password. A login is both halves, and the tracker preferences this
    // follows mark the username private for the same reason. The host and port above stay public:
    // they are the part worth keeping across a restore, and neither one is a secret.
    val username = preferenceStore.credentialString("animato_proxy_username")

    val password = preferenceStore.credentialString("animato_proxy_password")

    /**
     * The proxy as configured, or null when there is not one to use.
     *
     * Null rather than an exception for a half-filled form: the settings screen is where a blank
     * host gets complained about, and a network call reaching for this in the middle of a request
     * wants to know whether to use a proxy, not to be told the settings are wrong.
     *
     * Unresolved on purpose — [InetSocketAddress.createUnresolved] — so the name is looked up by
     * whoever connects rather than here. Resolving it on this thread would put a DNS lookup inside
     * a `ProxySelector.select` call, and resolving it *at all* would defeat the point for a SOCKS
     * proxy, which is meant to do the far end's DNS so the local resolver never sees the hostname.
     */
    fun proxy(): Proxy? {
        if (!enabled.get()) return null
        val address = host.get().trim().takeIf { it.isNotEmpty() } ?: return null
        val portNumber = port.get().trim().toIntOrNull()?.takeIf { it in 1..MAX_PORT } ?: return null
        val type = when (kind.get()) {
            ProxyKind.Socks5 -> Proxy.Type.SOCKS
            ProxyKind.Http -> Proxy.Type.HTTP
        }
        return Proxy(type, InetSocketAddress.createUnresolved(address, portNumber))
    }

    /**
     * The proxy as one URL, for the things that take it that way rather than as an object.
     *
     * mpv, which is the only caller today. Null for SOCKS, because there is nothing to hand it:
     * ffmpeg's `http-proxy` speaks HTTP to the proxy and has no SOCKS equivalent, so the honest
     * answer for a SOCKS user is *no proxy for playback* rather than an address that will be
     * dialled as if it were something it is not.
     *
     * Credentials go in the URL because that is the only place this form has for them, and mpv
     * hands the whole string to ffmpeg. They are already stored in the clear — see the class note —
     * so this moves them, it does not expose them further.
     */
    fun httpProxyUrl(): String? {
        if (kind.get() != ProxyKind.Http) return null
        val proxy = proxy() ?: return null
        val address = proxy.address() as? InetSocketAddress ?: return null
        val login = credentials()
            ?.let { (user, secret) -> "${user.encoded()}:${secret.encoded()}@" }
            .orEmpty()
        return "http://$login${address.hostString}:${address.port}"
    }

    /** The pair, or null if either half is missing. A username with no password is not a login. */
    fun credentials(): Pair<String, String>? {
        val user = username.get().trim().takeIf { it.isNotEmpty() } ?: return null
        val secret = password.get().takeIf { it.isNotEmpty() } ?: return null
        return user to secret
    }

    /**
     * Percent-encoded for the userinfo part of a URL.
     *
     * A proxy password with an `@` or a `:` in it would otherwise end the userinfo early and turn
     * the rest of the password into a hostname — a wrong address dialled with half a credential
     * attached, which fails in a way that looks like the proxy being down.
     */
    private fun String.encoded(): String = buildString {
        this@encoded.toByteArray().forEach { byte ->
            val char = byte.toInt().toChar()
            if (char.isLetterOrDigit() || char in UNRESERVED) {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }

    companion object {
        const val MAX_PORT = 65535

        private const val UNRESERVED = "-._~"
    }
}
