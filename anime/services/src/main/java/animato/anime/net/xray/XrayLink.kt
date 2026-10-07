package animato.anime.net.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

/**
 * One server, read out of the share link a provider hands out.
 *
 * ## The four link formats
 *
 * Nobody types an Xray configuration; providers give out a link, and every client accepts the same
 * four shapes. They are conventions rather than specifications, so this follows what the common
 * clients (v2rayN, v2rayNG, Hiddify) accept:
 *
 * - `vless://uuid@host:port?security=reality&pbk=…&sni=…&type=tcp#name` — everything is a query
 *   parameter, including the transport and the TLS or REALITY settings.
 * - `vmess://base64(json)` — one JSON object, base64-encoded, with short keys (`add`, `id`, `net`…).
 * - `trojan://password@host:port?security=tls&sni=…#name`
 * - `ss://base64(method:password)@host:port#name`, or the older form with the whole of
 *   `method:password@host:port` encoded.
 *
 * A link that cannot be read is an [IllegalArgumentException] with a sentence saying what was
 * wrong, because that sentence is what the settings screen shows.
 */
data class XrayLink(
    val protocol: Protocol,
    val name: String,
    val address: String,
    val port: Int,
    /** The UUID for vless and vmess, the password for trojan and shadowsocks. */
    val secret: String,
    /** vless: the flow (`xtls-rprx-vision`); vmess: the cipher; shadowsocks: the method. */
    val method: String? = null,
    val transport: Transport = Transport(),
    val security: Security = Security.None,
) {
    enum class Protocol { VLESS, VMESS, TROJAN, SHADOWSOCKS }

    /** How the bytes travel: plain TCP, WebSocket, gRPC, XHTTP or HTTPUpgrade. */
    data class Transport(
        val network: String = "tcp",
        val path: String? = null,
        val host: String? = null,
        /** gRPC's service name. */
        val serviceName: String? = null,
        /** gRPC's `multi`, or XHTTP's mode. */
        val mode: String? = null,
        /** TCP's header type — `http` for the camouflaged variant, otherwise `none`. */
        val headerType: String? = null,
    )

    sealed interface Security {
        data object None : Security

        data class Tls(
            val serverName: String?,
            val fingerprint: String?,
            val alpn: List<String>,
            val allowInsecure: Boolean,
        ) : Security

        data class Reality(
            val serverName: String?,
            val fingerprint: String?,
            val publicKey: String,
            val shortId: String?,
            val spiderX: String?,
        ) : Security
    }

    companion object {

        /** Reads [link], or throws [IllegalArgumentException] saying why it cannot. */
        fun parse(link: String): XrayLink {
            val text = link.trim()
            val scheme = text.substringBefore("://", missingDelimiterValue = "").lowercase()
            return when (scheme) {
                "vless" -> parseUrlStyle(text, Protocol.VLESS)
                "trojan" -> parseUrlStyle(text, Protocol.TROJAN)
                "vmess" -> parseVmess(text)
                "ss" -> parseShadowsocks(text)
                "" -> throw IllegalArgumentException(
                    "That is not a link. It should start with vless://, vmess://, trojan:// or ss://",
                )
                else -> throw IllegalArgumentException(
                    "$scheme:// links are not supported. Use vless, vmess, trojan or ss.",
                )
            }
        }

        private fun parseUrlStyle(text: String, protocol: Protocol): XrayLink {
            val uri = runCatching { URI(text.substringBefore('#')) }
                .getOrElse { throw IllegalArgumentException("The link is malformed: ${it.message}") }
            val secret = uri.rawUserInfo?.let(::decode)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "The link has no ${if (protocol == Protocol.VLESS) "user id" else "password"}.",
                )
            val host = uri.host?.removeSurrounding("[", "]")
                ?: throw IllegalArgumentException("The link has no server address.")
            val port = uri.port.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("The link has no valid port.")
            val query = query(uri.rawQuery)

            val defaultSecurity = if (protocol == Protocol.TROJAN) "tls" else "none"
            val security = when (query["security"]?.lowercase() ?: defaultSecurity) {
                "reality" -> Security.Reality(
                    serverName = query["sni"] ?: query["peer"],
                    fingerprint = query["fp"],
                    publicKey = query["pbk"]
                        ?: throw IllegalArgumentException("A reality link needs its public key (pbk)."),
                    shortId = query["sid"],
                    spiderX = query["spx"],
                )
                "tls", "xtls" -> Security.Tls(
                    serverName = query["sni"] ?: query["peer"],
                    fingerprint = query["fp"],
                    alpn = query["alpn"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    allowInsecure = query["allowInsecure"] == "1" || query["insecure"] == "1",
                )
                else -> Security.None
            }

            return XrayLink(
                protocol = protocol,
                name = fragment(text) ?: host,
                address = host,
                port = port,
                secret = secret,
                method = query["flow"]?.takeIf { protocol == Protocol.VLESS && it.isNotBlank() },
                transport = Transport(
                    network = normaliseNetwork(query["type"]),
                    path = query["path"],
                    host = query["host"],
                    serviceName = query["serviceName"],
                    mode = query["mode"],
                    headerType = query["headerType"],
                ),
                security = security,
            )
        }

        private fun parseVmess(text: String): XrayLink {
            val body = text.substringAfter("://").substringBefore('#')
            val json = runCatching { Json.parseToJsonElement(base64(body)) as JsonObject }
                .getOrElse { throw IllegalArgumentException("The vmess link could not be decoded.") }
            fun field(key: String) = json[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

            val address = field("add") ?: throw IllegalArgumentException("The vmess link has no server address.")
            val port = field("port")?.toIntOrNull()?.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("The vmess link has no valid port.")
            val id = field("id") ?: throw IllegalArgumentException("The vmess link has no user id.")
            val tls = field("tls")?.lowercase()
            return XrayLink(
                protocol = Protocol.VMESS,
                name = field("ps") ?: address,
                address = address,
                port = port,
                secret = id,
                method = field("scy") ?: "auto",
                transport = Transport(
                    network = normaliseNetwork(field("net")),
                    path = field("path"),
                    host = field("host"),
                    serviceName = field("path")?.takeIf { field("net") == "grpc" },
                    mode = field("type")?.takeIf { field("net") == "grpc" || field("net") == "xhttp" },
                    headerType = field("type")?.takeIf { field("net").orEmpty().ifEmpty { "tcp" } == "tcp" },
                ),
                security = if (tls == "tls") {
                    Security.Tls(
                        serverName = field("sni") ?: field("host"),
                        fingerprint = field("fp"),
                        alpn = field("alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                        allowInsecure = false,
                    )
                } else {
                    Security.None
                },
            )
        }

        private fun parseShadowsocks(text: String): XrayLink {
            val name = fragment(text)
            val body = text.substringAfter("://").substringBefore('#').substringBefore('?')
            // SIP002: ss://base64(method:password)@host:port — or the userinfo left unencoded.
            // Legacy: ss://base64(method:password@host:port).
            val decoded = if ('@' in body) {
                val userInfo = body.substringBeforeLast('@')
                val credentials = if (':' in decode(userInfo)) decode(userInfo) else base64(userInfo)
                "$credentials@${body.substringAfterLast('@')}"
            } else {
                base64(body)
            }
            val credentials = decoded.substringBeforeLast('@')
            val server = decoded.substringAfterLast('@')
            val method = credentials.substringBefore(':', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("The ss link has no cipher.")
            val password = credentials.substringAfter(':')
            val host = server.substringBeforeLast(':').removeSurrounding("[", "]")
                .takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("The ss link has no server address.")
            val port = server.substringAfterLast(':').toIntOrNull()?.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException("The ss link has no valid port.")
            return XrayLink(
                protocol = Protocol.SHADOWSOCKS,
                name = name ?: host,
                address = host,
                port = port,
                secret = password,
                method = method,
            )
        }

        /** `raw` is Xray's newer name for `tcp`, and `h2`/`http` are gone; read both spellings. */
        private fun normaliseNetwork(type: String?): String = when (type?.lowercase()?.takeIf { it.isNotBlank() }) {
            null, "tcp", "raw" -> "tcp"
            "ws", "websocket" -> "ws"
            "grpc", "gun" -> "grpc"
            "xhttp", "splithttp" -> "xhttp"
            "httpupgrade" -> "httpupgrade"
            "kcp", "mkcp" -> "kcp"
            else -> type.lowercase()
        }

        private fun query(raw: String?): Map<String, String> = raw.orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair -> decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', "")) }
            .filterValues { it.isNotEmpty() }

        private fun fragment(text: String): String? =
            text.substringAfter('#', "").takeIf { it.isNotBlank() }?.let(::decode)

        private fun decode(value: String): String = runCatching {
            URLDecoder.decode(value, "UTF-8")
        }.getOrDefault(value)

        /** Standard or URL-safe, padded or not — all four turn up in the wild. */
        private fun base64(value: String): String {
            val normalised = value.trim().replace('-', '+').replace('_', '/')
            val padded = normalised + "=".repeat((4 - normalised.length % 4) % 4)
            return runCatching { String(Base64.getDecoder().decode(padded)) }
                .getOrElse { throw IllegalArgumentException("Part of the link is not valid base64.") }
        }
    }
}
