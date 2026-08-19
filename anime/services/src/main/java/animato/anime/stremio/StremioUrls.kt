package animato.anime.stremio

/**
 * Where to send each question, and how to spell it.
 *
 * The Stremio transport is four paths hanging off a base URL:
 *
 * ```
 * /manifest.json
 * /catalog/{type}/{id}.json          /catalog/{type}/{id}/{extra}.json
 * /meta/{type}/{id}.json
 * /stream/{type}/{videoId}.json
 * ```
 *
 * Two details in there are easy to get wrong and impossible to notice by eye, which is why this
 * is its own file with its own tests. The first is that `{extra}` is a query string living in a
 * *path segment* — `search=game%20of%20thrones&skip=100` — so it is neither a real query string
 * nor plain text. The second is that ids routinely contain colons (`kitsu:1234`, and every series
 * episode id looks like `tt0944947:1:1`), so every segment has to be percent-encoded on the way
 * out and the base has to be reassembled by hand rather than by a URL builder that would
 * re-encode what we already encoded.
 */
object StremioUrls {

    /**
     * Reduce whatever the user pasted to the addon's base URL.
     *
     * People copy three things: the `manifest.json` link the addon's own page hands out, the bare
     * host, or a `stremio://` deep link. All three name the same addon. What must survive is any
     * path *before* `manifest.json` — configurable addons put their entire configuration there
     * (`https://torrentio.strem.fun/providers=yts,eztv/manifest.json`), so trimming to the host
     * would quietly hand back a differently-configured addon.
     */
    fun normalizeBase(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return ""
        if (url.startsWith(STREMIO_SCHEME, ignoreCase = true)) {
            url = "https://" + url.substring(STREMIO_SCHEME.length)
        }
        if (!url.contains("://")) {
            url = "https://$url"
        }
        url = url.trimEnd('/')
        if (url.endsWith(MANIFEST_PATH, ignoreCase = true)) {
            url = url.dropLast(MANIFEST_PATH.length)
        }
        return url.trimEnd('/')
    }

    fun manifest(base: String): String = normalizeBase(base) + MANIFEST_PATH

    fun catalog(
        base: String,
        type: String,
        id: String,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val prefix = "${normalizeBase(base)}/catalog/${type.encodeUriComponent()}/${id.encodeUriComponent()}"
        val present = extra.filterValues { it.isNotBlank() }
        if (present.isEmpty()) return "$prefix.json"
        val args = present.entries.joinToString("&") { (key, value) ->
            "${key.encodeUriComponent()}=${value.encodeUriComponent()}"
        }
        return "$prefix/$args.json"
    }

    fun meta(base: String, type: String, id: String): String =
        "${normalizeBase(base)}/meta/${type.encodeUriComponent()}/${id.encodeUriComponent()}.json"

    fun stream(base: String, type: String, videoId: String): String =
        "${normalizeBase(base)}/stream/${type.encodeUriComponent()}/${videoId.encodeUriComponent()}.json"

    /**
     * Subtitles take extra arguments the way catalogs do, and for the same reason: the addon needs
     * more than an id to answer well. A subtitle provider matches on the file — its size, its name
     * — so a release with a different cut gets subtitles timed for *that* cut rather than for
     * whatever else shares the title.
     */
    fun subtitles(
        base: String,
        type: String,
        videoId: String,
        extra: Map<String, String> = emptyMap(),
    ): String {
        val prefix = "${normalizeBase(base)}/subtitles/${type.encodeUriComponent()}/${videoId.encodeUriComponent()}"
        val present = extra.filterValues { it.isNotBlank() }
        if (present.isEmpty()) return "$prefix.json"
        val args = present.entries.joinToString("&") { (key, value) ->
            "${key.encodeUriComponent()}=${value.encodeUriComponent()}"
        }
        return "$prefix/$args.json"
    }

    private const val STREMIO_SCHEME = "stremio://"
    private const val MANIFEST_PATH = "/manifest.json"
}

/**
 * Percent-encode one path segment the way JavaScript's `encodeURIComponent` does.
 *
 * Not [java.net.URLEncoder]: that one is for form bodies, so it writes a space as `+` and escapes
 * `~` and `*`. Addons are written against the JS function and matched against by string in more
 * than a few of them, so `+` where `%20` belongs is a lookup that silently returns nothing.
 */
internal fun String.encodeUriComponent(): String {
    val bytes = toByteArray(Charsets.UTF_8)
    return buildString(bytes.size) {
        for (byte in bytes) {
            val char = byte.toInt().toChar()
            if (byte >= 0 && char in UNRESERVED) {
                append(char)
            } else {
                val value = byte.toInt() and 0xFF
                append('%').append(HEX[value shr 4]).append(HEX[value and 0x0F])
            }
        }
    }
}

private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"

private const val HEX = "0123456789ABCDEF"
