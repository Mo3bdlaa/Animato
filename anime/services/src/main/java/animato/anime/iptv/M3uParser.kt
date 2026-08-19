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
                    if (line.startsWith(EXTGRP, ignoreCase = true)) {
                        pending = pending?.copy(group = line.substringAfter(':').trim().ifBlank { null })
                    }
                }

                else -> {
                    val entry = pending ?: return@forEach
                    pending = null
                    val id = entry.tvgId?.takeIf { it.isNotBlank() } ?: line
                    // A playlist repeating a channel is ordinary — several qualities of the same
                    // one, or the same one in two groups — and two library rows with one id is
                    // not. First wins, which is the highest quality in every playlist that does
                    // this deliberately.
                    if (!seen.add(id)) return@forEach
                    channels += M3uChannel(
                        id = id,
                        name = entry.name.ifBlank { line },
                        url = line,
                        logo = entry.logo,
                        group = entry.group,
                        tvgId = entry.tvgId,
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
    )

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
    private val ATTRIBUTE = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")
}
