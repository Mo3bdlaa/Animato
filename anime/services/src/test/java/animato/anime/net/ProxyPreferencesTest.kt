package animato.anime.net

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * The two things here that can be wrong without looking wrong.
 *
 * A half-filled form must not produce a proxy — the switch being on with no address typed is the
 * ordinary state of somebody in the middle of setting one up, and answering with a proxy pointed at
 * nothing would take the whole app offline while every screen said only that the site was
 * unreachable.
 *
 * And a password with punctuation in it must survive being put in a URL for mpv. `p@ss` written
 * plainly ends the userinfo at the `@`, so ffmpeg dials a host called `ss` — a failure that reads
 * exactly like the proxy being down, on the one code path nobody can step through.
 */
@Execution(ExecutionMode.CONCURRENT)
class ProxyPreferencesTest {

    private fun preferences(
        enabled: Boolean = true,
        kind: ProxyKind = ProxyKind.Http,
        host: String = "proxy.example.test",
        port: String = "8080",
        username: String = "",
        password: String = "",
    ) = ProxyPreferences(InMemoryPreferenceStore()).apply {
        this.enabled.set(enabled)
        this.kind.set(kind)
        this.host.set(host)
        this.port.set(port)
        this.username.set(username)
        this.password.set(password)
    }

    @Test
    fun `a complete configuration becomes a proxy of the right kind`() {
        val socks = preferences(kind = ProxyKind.Socks5, host = "10.0.0.2", port = "1080").proxy()
        socks?.type() shouldBe Proxy.Type.SOCKS
        (socks?.address() as InetSocketAddress).port shouldBe 1080

        preferences(kind = ProxyKind.Http).proxy()?.type() shouldBe Proxy.Type.HTTP
    }

    @Test
    fun `the address is left unresolved so the proxy does the lookup`() {
        val address = preferences().proxy()?.address() as InetSocketAddress
        // The whole point of a SOCKS proxy is that the far end resolves the name; resolving it here
        // would leak every hostname the app visits to the local resolver it is meant to bypass.
        address.isUnresolved shouldBe true
        address.hostString shouldBe "proxy.example.test"
    }

    @Test
    fun `a half-filled form is not a proxy`() {
        preferences(enabled = false).proxy() shouldBe null
        preferences(host = "").proxy() shouldBe null
        preferences(host = "   ").proxy() shouldBe null
        preferences(port = "").proxy() shouldBe null
        preferences(port = "not a port").proxy() shouldBe null
        // Out of range at both ends. Port 0 means "any free port" to a listener and means nothing
        // at all to something being dialled.
        preferences(port = "0").proxy() shouldBe null
        preferences(port = "65536").proxy() shouldBe null
    }

    @Test
    fun `credentials need both halves`() {
        preferences(username = "me", password = "secret").credentials() shouldBe ("me" to "secret")
        preferences(username = "me").credentials() shouldBe null
        preferences(password = "secret").credentials() shouldBe null
        preferences(username = "  ", password = "secret").credentials() shouldBe null
    }

    @Test
    fun `the mpv url carries credentials without letting punctuation break it`() {
        preferences(username = "me", password = "secret").httpProxyUrl() shouldBe
            "http://me:secret@proxy.example.test:8080"

        // The one that matters: an unescaped @ would end the userinfo early and turn the rest of
        // the password into the hostname.
        preferences(username = "me", password = "p@ss:word").httpProxyUrl() shouldBe
            "http://me:p%40ss%3Aword@proxy.example.test:8080"

        preferences().httpProxyUrl() shouldBe "http://proxy.example.test:8080"
    }

    @Test
    fun `a SOCKS proxy offers mpv nothing, because mpv cannot use one`() {
        // Not the address written as if it were HTTP: ffmpeg would speak HTTP at a SOCKS port and
        // fail in a way that looks like the proxy rejecting the stream.
        preferences(kind = ProxyKind.Socks5).httpProxyUrl() shouldBe null
    }
}
