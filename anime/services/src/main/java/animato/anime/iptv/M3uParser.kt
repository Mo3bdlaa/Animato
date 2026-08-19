package animato.anime.iptv

/**
 * One channel out of a playlist.
 *
 * [id] is the playlist's own `tvg-id` where it has one and the stream address otherwise. It is the
 * thing a library row is keyed on, so it has to survive a playlist being refetched: a provider
 * reorders its file constantly and renumbers nothing, and keying on position would move every
 * channel in somebody's library the first time that happened.
 */
data class M3uChannel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String? = null,
    /** The XMLTV id, kept separately from [id] because a guide is matched on this and only this. */
    val tvgId: String? = null,
    /**
     * HTTP headers this channel needs to be fetched with.
     *
     * The single commonest reason a playlist "does not work": the provider checks the `User-Agent`
     * or the `Referer` and answers 403 to anything else. That requirement is not a secret and not
     * something to guess at — the playlist states it, in any of three conventions, and all three
     * are read here. See [parseHeaderDirective] and [splitPipeOptions].
     */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * An M3U playlist, parsed.
 *
 * ## Why this is hand-written and not a library
 *
 * The format is barely a format. There is no specification anybody follows: the header is
 * `#EXTM3U`, a channel is an `#EXTINF` line carrying arbitrary `key="value"` attributes and a
 * display name after a comma, and the next non-comment line is its address. Providers disagree
 * about quoting, about which attributes exist, about whether the duration field is `-1` or `0`, and
 * about whether the name after the comma matches `tvg-name` or contradicts it.
 *
 * So the parser is deliberately forgiving in one direction only: anything it cannot understand is
 * skipped, and nothing it does understand is guessed at. A line without a URL after it is dropped
 * rather than pointed at nothing.
 */
object M3uParser {

    /**
     * Every channel in [text], in the order the file lists them.
     *
     * Order is kept because a playlist's order is editorial — providers put the popular channels
     * first — and there is nothing better to sort by. Names are localised, numbers are absent, and
     * alphabetical would bury the channels somebody actually wants under a hundred shopping ones.
     */
    fun parse(text: String): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        var pending: PendingChannel? = null
        val seen = mutableSetOf<String>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith(EXTINF, ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }

                // Every other directive. `#EXTGRP` is the one that carries information we want —
                // some playlists put the group there instead of in an attribute — and the rest
                // (`#EXTVLCOPT`, `#PLAYLIST`, `#EXTM3U` itself) are skipped without disturbing the
                // channel being assembled, because they sit *between* the EXTINF and its URL.
                line.startsWith("#") -> {
                    when {
                        line.startsWith(EXTGRP, ignoreCase = true) -> {
                            pending = pending?.copy(
                                group = line.substringAfter(':').trim().ifBlank { null },
                            )
                        }
                        else -> parseHeaderDirective(line)?.let { found ->
                            pending = pending?.let { it.copy(headers = it.headers + found) }
                        }
                    }
                }

                else -> {
                    val entry = pending ?: return@forEach
                    pending = null
                    // Kodi's convention puts the headers on the end of the address itself. The
                    // pipe and everything after it is not part of the URL and would 404 if it
                    // were sent, so it is split off here rather than anywhere later.
                    val (address, inlineHeaders) = splitPipeOptions(line)
                    val id = entry.tvgId?.takeIf { it.isNotBlank() } ?: address
                    // A playlist repeating a channel is ordinary — several qualities of the same
                    // one, or the same one in two groups — and two library rows with one id is
                    // not. First wins, which is the highest quality in every playlist that does
                    // this deliberately.
                    if (!seen.add(id)) return@forEach
                    channels += M3uChannel(
                        id = id,
                        name = entry.name.ifBlank { address },
                        url = address,
                        logo = entry.logo,
                        group = entry.group,
                        tvgId = entry.tvgId,
                        // The address wins where both say the same thing: it is the more specific
                        // of the two, being attached to this one stream rather than to the entry.
                        headers = entry.headers + inlineHeaders,
                    )
                }
            }
        }
        return channels
    }

    /** The groups the playlist uses, in first-seen order, for the filter sheet. */
    fun groupsOf(channels: List<M3uChannel>): List<String> =
        channels.mapNotNull { it.group?.takeIf { group -> group.isNotBlank() } }.distinct()

    private data class PendingChannel(
        val name: String,
        val logo: String?,
        val group: String?,
        val tvgId: String?,
        val headers: Map<String, String> = emptyMap(),
    )

    /**
     * A header stated by a directive between the entry and its address, or nothing.
     *
     * Two conventions, both common. `#EXTVLCOPT:http-user-agent=…` is VLC's, one header per line
     * and named for the player rather than for HTTP. `#EXTHTTP:{"User-Agent":"…"}` is a JSON
     * object of them, and is read by hand rather than parsed as JSON: it is one flat object of
     * strings, and pulling a serializer into a text parser for that would be the larger cost.
     */
    private fun parseHeaderDirective(line: String): Map<String, String>? = when {
        line.startsWith(EXTVLCOPT, ignoreCase = true) -> {
            val option = line.substringAfter(':', "").trim()
            val key = option.substringBefore('=', "").lowercase()
            val value = option.substringAfter('=', "").trim()
            VLC_HEADERS[key]?.takeIf { value.isNotEmpty() }?.let { mapOf(it to value) }
        }
        line.startsWith(EXTHTTP, ignoreCase = true) -> {
            JSON_PAIR.findAll(line.substringAfter(':', ""))
                .associate { it.groupValues[1] to it.groupValues[2] }
                .takeIf { it.isNotEmpty() }
        }
        else -> null
    }

    /**
     * An address split from the `|Key=Value` options Kodi appends to it.
     *
     * Only when what follows the pipe actually looks like options. A pipe is legal in a URL and
     * appears in query strings, so a suffix with no `=` in it is left where it was rather than
     * silently truncating somebody's stream address.
     */
    private fun splitPipeOptions(line: String): Pair<String, Map<String, String>> {
        val pipe = line.indexOf('|')
        if (pipe < 0) return line to emptyMap()
        val options = line.substring(pipe + 1)
            .split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").trim()
                val value = part.substringAfter('=', "").trim()
                if (key.isEmpty() || value.isEmpty()) null else key to value
            }
            .toMap()
        return if (options.isEmpty()) line to emptyMap() else line.substring(0, pipe) to options
    }

    /**
     * One `#EXTINF` line: its attributes, and the display name after the comma.
     *
     * The name is taken from after the **last** comma rather than the first, because attribute
     * values contain commas often — a `group-title="Movies, Drama"` is entirely normal — and
     * splitting on the first would name the channel after half its own metadata.
     */
    private fun parseExtInf(line: String): PendingChannel {
        val comma = line.lastIndexOf(',')
        val attributes = parseAttributes(if (comma >= 0) line.substring(0, comma) else line)
        val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
        return PendingChannel(
            name = name.ifBlank { attributes["tvg-name"].orEmpty() },
            logo = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
            group = attributes["group-title"]?.takeIf { it.isNotBlank() },
            tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * `key="value"` pairs, lowercased on the key.
     *
     * Only the quoted form. Unquoted values appear in the wild and cannot be delimited reliably —
     * a value with a space in it is indistinguishable from the start of the next attribute — so
     * they are left alone rather than parsed into something plausible and wrong.
     */
    private fun parseAttributes(text: String): Map<String, String> =
        ATTRIBUTE.findAll(text).associate { match ->
            match.groupValues[1].lowercase() to match.groupValues[2]
        }

    private const val EXTINF = "#EXTINF"
    private const val EXTGRP = "#EXTGRP"
    private const val EXTVLCOPT = "#EXTVLCOPT"
    private const val EXTHTTP = "#EXTHTTP"

    /** VLC's option names, and the HTTP headers they mean. */
    private val VLC_HEADERS = mapOf(
        "http-user-agent" to "User-Agent",
        "http-referrer" to "Referer",
        // Spelled correctly by some providers, and by the HTTP standard incorrectly. Both appear.
        "http-referer" to "Referer",
        "http-origin" to "Origin",
    )

    private val JSON_PAIR = Regex(""""([^"]+)"\s*:\s*"([^"]*)"""")
    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
}
