package animato.anime.net.xray

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Base64

class XrayLinkTest {

    @Test
    fun `a vless reality link`() {
        val link = XrayLink.parse(REALITY)

        link.protocol shouldBe XrayLink.Protocol.VLESS
        link.address shouldBe "example.com"
        link.port shouldBe 443
        link.secret shouldBe "b831381d-6324-4d53-ad4f-8cda48b30811"
        link.method shouldBe "xtls-rprx-vision"
        link.name shouldBe "My server"
        val reality = link.security.shouldBeInstanceOf<XrayLink.Security.Reality>()
        reality.publicKey shouldBe "kU8tKTzLcKFGlgRTVqDdFDHvLgSG6tbTH7d7oyNzSnc"
        reality.serverName shouldBe "www.microsoft.com"
        reality.shortId shouldBe "6ba85179e30d4fc2"
    }

    @Test
    fun `a vless websocket tls link`() {
        val link = XrayLink.parse(
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@1.2.3.4:8443?encryption=none&security=tls" +
                "&sni=cdn.example.com&type=ws&host=cdn.example.com&path=%2Fws%3Fed%3D2048#ws",
        )
        link.transport.network shouldBe "ws"
        link.transport.path shouldBe "/ws?ed=2048"
        link.security.shouldBeInstanceOf<XrayLink.Security.Tls>().serverName shouldBe "cdn.example.com"
    }

    @Test
    fun `a vmess link`() {
        val link = XrayLink.parse(VMESS)
        link.protocol shouldBe XrayLink.Protocol.VMESS
        link.address shouldBe "vm.example.com"
        link.port shouldBe 443
        link.transport.network shouldBe "ws"
        link.transport.path shouldBe "/v"
        link.security.shouldBeInstanceOf<XrayLink.Security.Tls>()
        link.name shouldBe "vmess server"
    }

    @Test
    fun `a trojan link defaults to tls`() {
        val link = XrayLink.parse(TROJAN)
        link.protocol shouldBe XrayLink.Protocol.TROJAN
        link.secret shouldBe "p@ss word"
        link.security.shouldBeInstanceOf<XrayLink.Security.Tls>()
    }

    @Test
    fun `both shadowsocks forms`() {
        val sip002 = XrayLink.parse(SHADOWSOCKS)
        sip002.method shouldBe "chacha20-ietf-poly1305"
        sip002.secret shouldBe "secret"
        sip002.address shouldBe "ss.example.com"
        sip002.port shouldBe 8388

        val legacy = XrayLink.parse(
            "ss://" + Base64.getEncoder().encodeToString("aes-256-gcm:pw@5.6.7.8:443".toByteArray()) + "#old",
        )
        legacy.method shouldBe "aes-256-gcm"
        legacy.address shouldBe "5.6.7.8"
        legacy.port shouldBe 443
    }

    @Test
    fun `what cannot be read says why`() {
        shouldThrow<IllegalArgumentException> { XrayLink.parse("hysteria2://x@y:1") }.message shouldContain
            "not supported"
        shouldThrow<IllegalArgumentException> { XrayLink.parse("hello") }.message shouldContain "not a link"
        shouldThrow<IllegalArgumentException> {
            XrayLink.parse("vless://id@host:443?security=reality&sni=a.com")
        }.message shouldContain "public key"
    }

    @Test
    fun `the configuration listens locally and sends private addresses direct`() {
        val config = Json.parseToJsonElement(XrayConfig.build(XrayLink.parse(REALITY))).jsonObject
        val inbounds = config["inbounds"]!!.jsonArray.map { it.jsonObject }
        inbounds.map { it["listen"]!!.jsonPrimitive.content }.toSet() shouldBe setOf("127.0.0.1")
        inbounds.map { it["port"]!!.jsonPrimitive.content.toInt() } shouldBe
            listOf(XrayConfig.SOCKS_PORT, XrayConfig.HTTP_PORT)
        val proxy = config["outbounds"]!!.jsonArray.first().jsonObject
        proxy["streamSettings"]!!.jsonObject["security"]!!.jsonPrimitive.content shouldBe "reality"
        config["routing"].toString() shouldContain "192.168.0.0/16"
    }

    /**
     * Writes every sample's configuration where the Go validator can read it — see
     * animato-xray/cmd/validate. Only when asked, since it is a cross-language check rather than a
     * unit test.
     */
    @Test
    fun `dump configurations for the Xray validator`() {
        val directory = System.getenv("XRAY_CONFIG_DUMP") ?: return
        File(directory).mkdirs()
        listOf(REALITY, VMESS, TROJAN, SHADOWSOCKS, LOCAL_WS).forEachIndexed { index, sample ->
            File(directory, "config-$index.json").writeText(XrayConfig.build(XrayLink.parse(sample)))
        }
    }

    private companion object {
        const val REALITY =
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@example.com:443?encryption=none" +
                "&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com&fp=chrome" +
                "&pbk=kU8tKTzLcKFGlgRTVqDdFDHvLgSG6tbTH7d7oyNzSnc&sid=6ba85179e30d4fc2&type=tcp#My%20server"

        val VMESS = "vmess://" + Base64.getEncoder().encodeToString(
            """{"v":"2","ps":"vmess server","add":"vm.example.com","port":"443","id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto","net":"ws","type":"none","host":"vm.example.com","path":"/v","tls":"tls","sni":"vm.example.com"}"""
                .toByteArray(),
        )

        /** A plain vless-over-WebSocket server on this machine, for the end-to-end check in the README. */
        const val LOCAL_WS =
            "vless://b831381d-6324-4d53-ad4f-8cda48b30811@127.0.0.1:9443?encryption=none&security=none" +
                "&type=ws&path=%2Fanimato#local"

        const val TROJAN = "trojan://p%40ss%20word@tr.example.com:443?sni=tr.example.com#trojan"

        val SHADOWSOCKS = "ss://" +
            Base64.getUrlEncoder().withoutPadding().encodeToString("chacha20-ietf-poly1305:secret".toByteArray()) +
            "@ss.example.com:8388#ss"
    }
}
