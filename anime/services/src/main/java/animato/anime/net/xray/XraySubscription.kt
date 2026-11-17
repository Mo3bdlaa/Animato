package animato.anime.net.xray

import java.util.Base64

/**
 * A subscription: one address that lists many servers, the way Hiddify and v2rayNG take them.
 *
 * The body is a list of share links, one per line, usually base64-encoded as a whole. Lines that
 * are not a link this app can run (Clash or sing-box entries, comments, protocols Xray here does
 * not speak) are left out rather than failing the list.
 */
object XraySubscription {

    /** The runnable share links in [body], in the order the subscription lists them. */
    fun parse(body: String): List<String> {
        val text = body.trim()
        val lines = if (text.contains("://")) text else decode(text) ?: return emptyList()
        return lines.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && runCatching { XrayLink.parse(it) }.isSuccess }
            .distinct()
            .toList()
    }

    /**
     * Which of [links] to run after a refresh: the one already running when it is still listed,
     * else the one with the same name (a server whose details changed), else the first.
     */
    fun reselect(current: String, links: List<String>): String? {
        if (links.isEmpty()) return null
        if (current in links) return current
        val name = runCatching { XrayLink.parse(current).name }.getOrNull()
        return links.firstOrNull { name != null && runCatching { XrayLink.parse(it).name }.getOrNull() == name }
            ?: links.first()
    }

    private fun decode(text: String): String? {
        val compact = text.filterNot(Char::isWhitespace)
        val padded = compact.padEnd((compact.length + 3) / 4 * 4, '=')
        return runCatching { String(Base64.getDecoder().decode(padded)) }
            .recoverCatching { String(Base64.getUrlDecoder().decode(padded)) }
            .getOrNull()
    }
}
