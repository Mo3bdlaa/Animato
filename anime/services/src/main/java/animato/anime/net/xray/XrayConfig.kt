package animato.anime.net.xray

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The Xray configuration for one server, as the app runs it: a local proxy and nothing more.
 *
 * ## Shape
 *
 * Two inbounds on the loopback address only — SOCKS and HTTP, on fixed ports nothing else is
 * likely to use — and two outbounds: the server from the link, and direct. HTTP is there because
 * the player can only use an HTTP proxy (ffmpeg's `http-proxy` has no SOCKS form), and the app's
 * own requests use it too, so one port serves everything.
 *
 * Private and loopback addresses go direct. The torrent server and the local HTTP server the
 * player streams from are on `127.0.0.1`, and a home media server is on the LAN; sending either
 * through a server abroad would break them. Written as CIDRs rather than `geoip:private` because
 * that needs geo data files, which are megabytes the app does not otherwise carry.
 *
 * No TUN, no system DNS: this is a proxy that the app chooses to use, not a VPN. Hostnames reach
 * Xray inside the proxy request and are resolved by the server, so the local resolver never sees
 * them.
 */
object XrayConfig {

    /** Uncommon on purpose: v2rayNG and most desktop clients default to 10808/10809. */
    const val SOCKS_PORT = 47820
    const val HTTP_PORT = 47821
    const val LISTEN = "127.0.0.1"

    private val PRIVATE_RANGES = listOf(
        "127.0.0.0/8",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "169.254.0.0/16",
        "::1/128",
        "fc00::/7",
        "fe80::/10",
    )

    fun build(link: XrayLink): String = buildJsonObject {
        putJsonObject("log") { put("loglevel", "warning") }
        putJsonArray("inbounds") {
            addJsonObject {
                put("tag", "socks-in")
                put("listen", LISTEN)
                put("port", SOCKS_PORT)
                put("protocol", "socks")
                putJsonObject("settings") {
                    put("auth", "noauth")
                    put("udp", true)
                }
            }
            addJsonObject {
                put("tag", "http-in")
                put("listen", LISTEN)
                put("port", HTTP_PORT)
                put("protocol", "http")
            }
        }
        putJsonArray("outbounds") {
            add(outbound(link))
            addJsonObject {
                put("tag", "direct")
                put("protocol", "freedom")
            }
        }
        putJsonObject("routing") {
            put("domainStrategy", "AsIs")
            putJsonArray("rules") {
                addJsonObject {
                    put("type", "field")
                    put("ip", JsonArray(PRIVATE_RANGES.map(::JsonPrimitive)))
                    put("outboundTag", "direct")
                }
                addJsonObject {
                    put("type", "field")
                    put("domain", buildJsonArray { add("localhost") })
                    put("outboundTag", "direct")
                }
            }
        }
    }.toString()

    internal fun outbound(link: XrayLink): JsonObject = buildJsonObject {
        put("tag", "proxy")
        when (link.protocol) {
            XrayLink.Protocol.VLESS -> {
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        addJsonObject {
                            put("address", link.address)
                            put("port", link.port)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", link.secret)
                                    put("encryption", "none")
                                    link.method?.let { put("flow", it) }
                                }
                            }
                        }
                    }
                }
            }
            XrayLink.Protocol.VMESS -> {
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        addJsonObject {
                            put("address", link.address)
                            put("port", link.port)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", link.secret)
                                    put("security", link.method ?: "auto")
                                }
                            }
                        }
                    }
                }
            }
            XrayLink.Protocol.TROJAN -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", link.address)
                            put("port", link.port)
                            put("password", link.secret)
                        }
                    }
                }
            }
            XrayLink.Protocol.SHADOWSOCKS -> {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", link.address)
                            put("port", link.port)
                            put("method", link.method)
                            put("password", link.secret)
                        }
                    }
                }
            }
        }
        if (link.protocol != XrayLink.Protocol.SHADOWSOCKS) {
            put("streamSettings", streamSettings(link))
        }
    }

    private fun streamSettings(link: XrayLink): JsonObject = buildJsonObject {
        val transport = link.transport
        put("network", transport.network)
        when (val security = link.security) {
            XrayLink.Security.None -> put("security", "none")
            is XrayLink.Security.Tls -> {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    (security.serverName ?: transport.host)?.let { put("serverName", it) }
                    security.fingerprint?.let { put("fingerprint", it) }
                    if (security.alpn.isNotEmpty()) put("alpn", JsonArray(security.alpn.map(::JsonPrimitive)))
                    if (security.allowInsecure) put("allowInsecure", true)
                }
            }
            is XrayLink.Security.Reality -> {
                put("security", "reality")
                putJsonObject("realitySettings") {
                    security.serverName?.let { put("serverName", it) }
                    // REALITY refuses to start without a fingerprint; chrome is what clients default to.
                    put("fingerprint", security.fingerprint ?: "chrome")
                    put("publicKey", security.publicKey)
                    security.shortId?.let { put("shortId", it) }
                    security.spiderX?.let { put("spiderX", it) }
                }
            }
        }
        when (transport.network) {
            "ws" -> putJsonObject("wsSettings") {
                transport.path?.let { put("path", it) }
                transport.host?.let { put("host", it) }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                (transport.serviceName ?: transport.path)?.let { put("serviceName", it) }
                if (transport.mode == "multi") put("multiMode", true)
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                transport.path?.let { put("path", it) }
                transport.host?.let { put("host", it) }
                transport.mode?.let { put("mode", it) }
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                transport.path?.let { put("path", it) }
                transport.host?.let { put("host", it) }
            }
            "tcp" -> if (transport.headerType == "http") {
                putJsonObject("tcpSettings") {
                    putJsonObject("header") {
                        put("type", "http")
                        putJsonObject("request") {
                            putJsonArray("path") { add(transport.path ?: "/") }
                            transport.host?.let { host ->
                                putJsonObject("headers") {
                                    putJsonArray("Host") { host.split(',').forEach { add(it.trim()) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
