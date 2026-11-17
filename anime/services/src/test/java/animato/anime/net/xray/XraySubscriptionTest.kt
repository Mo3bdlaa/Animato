package animato.anime.net.xray

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Base64

class XraySubscriptionTest {

    private val vless = "vless://11111111-2222-3333-4444-555555555555@a.example.test:443" +
        "?security=reality&pbk=abc&sni=www.youtube.com&type=tcp#France%20(direct%2C%20fastest)"
    private val trojan = "trojan://secret@b.example.test:443?security=tls&sni=b.example.test#Proton%20NL"
    private val trojanMoved = "trojan://secret@c.example.test:443?security=tls&sni=c.example.test#Proton%20NL"

    @Test
    fun `reads a base64 list and skips what it cannot run`() {
        val body = listOf(vless, "# a comment", "hysteria2://x@y:1#nope", trojan).joinToString("\n")
        val encoded = Base64.getEncoder().encodeToString(body.toByteArray())

        XraySubscription.parse(encoded) shouldBe listOf(vless, trojan)
    }

    @Test
    fun `reads url-safe base64 without padding, wrapped over lines`() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString("$trojan\n".toByteArray())
        val wrapped = encoded.chunked(20).joinToString("\n")

        XraySubscription.parse(wrapped) shouldBe listOf(trojan)
    }

    @Test
    fun `reads a plain list`() {
        XraySubscription.parse("$trojan\r\n$vless\n") shouldBe listOf(trojan, vless)
    }

    @Test
    fun `something that is not a subscription lists nothing`() {
        XraySubscription.parse("<html>not found</html>") shouldBe emptyList()
    }

    @Test
    fun `keeps the running server when it is still listed`() {
        XraySubscription.reselect(trojan, listOf(vless, trojan)) shouldBe trojan
    }

    @Test
    fun `follows a server by name when its details changed`() {
        XraySubscription.reselect(trojan, listOf(vless, trojanMoved)) shouldBe trojanMoved
    }

    @Test
    fun `falls back to the first server, or none`() {
        XraySubscription.reselect("", listOf(vless, trojan)) shouldBe vless
        XraySubscription.reselect(trojan, emptyList()) shouldBe null
    }
}
