package animato.anime.torznab

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/**
 * One release an indexer knows about.
 *
 * A *release*, not a title. This is the whole shape of the Torznab world and the thing that decides
 * how it maps onto the app: an indexer does not know what a series is, it knows about files
 * somebody uploaded. Two rips of the same episode are two releases, and a season pack is one.
 */
data class TorznabRelease(
    val title: String,
    val magnet: String,
    val category: String?,
    val sizeBytes: Long,
    val seeders: Int,
    val publishedAt: Long,
) {
    /**
     * The identity of a release, for a library row.
     *
     * The info hash where there is one, because that is what the release *is* — the same torrent
     * offered by two indexers is one thing, and a title that gets retagged upstream should not
     * become a second row in somebody's library. The title is the fallback for the rare indexer
     * that returns only a `.torrent` link.
     */
    val id: String get() = TorznabFeed.infoHashOf(magnet) ?: title
}

/**
 * The parts of a Torznab capabilities document worth reading.
 *
 * Only the categories. The rest of `t=caps` describes search modes and supported parameters, and
 * this app asks the same simple question of every indexer, so there is nothing to negotiate.
 */
data class TorznabCaps(
    val categories: List<TorznabCategory>,
)

data class TorznabCategory(
    val id: String,
    val name: String,
)

/**
 * Torznab's XML, read.
 *
 * ## Why jsoup rather than a real XML parser
 *
 * It is already a dependency, and what arrives here is not reliably well-formed XML. Torznab is
 * RSS with a namespaced extension, and indexers proxied through Jackett routinely emit release
 * titles containing raw `&` and unescaped angle brackets — a strict parser rejects the whole
 * document over one bad title, which on screen is an indexer that "returns nothing" while working
 * perfectly for every other client. jsoup in XML mode is lenient in exactly the way this needs.
 *
 * ## What a torznab:attr is
 *
 * Everything Torznab adds to RSS rides in repeated `<torznab:attr name="x" value="y"/>` elements
 * rather than in elements of their own. So the magnet link, the seeder count and the info hash are
 * all found by attribute name, and an indexer that omits one omits the element entirely rather than
 * sending an empty one.
 */
object TorznabFeed {

    fun parseCaps(xml: String): TorznabCaps {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val categories = document.select("categories > category").flatMap { category ->
            val id = category.attr("id")
            val name = category.attr("name")
            // The subcategories matter more than the parents for anime: an indexer files everything
            // under 5000 (TV) and distinguishes anime only at 5070, so offering the parent alone
            // would offer one chip meaning "television" to an app whose subject is a part of it.
            val children = category.select("subcat").map {
                TorznabCategory(id = it.attr("id"), name = "$name · ${it.attr("name")}")
            }
            if (id.isBlank()) children else listOf(TorznabCategory(id, name)) + children
        }
        return TorznabCaps(categories.filter { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    /**
     * The releases in a search response.
     *
     * A release with no magnet and no info hash is dropped rather than listed. This app plays
     * torrents through the bundled torrent server and has nothing to do with a `.torrent` file it
     * would have to fetch and pass along, so listing one would be a row that fails on tap — and
     * "it doesn't play" is the least diagnosable failure this app has.
     */
    fun parseSearch(xml: String): List<TorznabRelease> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return document.select("item").mapNotNull { item ->
            val title = item.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null

            val attrs = item.select("*|attr").associate { it.attr("name") to it.attr("value") }
            val magnet = attrs["magneturl"]?.takeIf { it.startsWith(MAGNET_SCHEME) }
                ?: item.selectFirst("link")?.text()?.takeIf { it.startsWith(MAGNET_SCHEME) }
                ?: attrs["infohash"]?.takeIf { it.isNotBlank() }?.let { magnetFor(it, title) }
                ?: return@mapNotNull null

            TorznabRelease(
                title = title,
                magnet = magnet,
                category = attrs["category"],
                // The size is in `<size>` on some indexers and in an attr on others, and it is the
                // single most useful number for choosing between two releases of the same thing.
                sizeBytes = (item.selectFirst("size")?.text() ?: attrs["size"])?.toLongOrNull() ?: 0L,
                seeders = attrs["seeders"]?.toIntOrNull() ?: 0,
                publishedAt = parseRfc822(item.selectFirst("pubDate")?.text()),
            )
        }
    }

    /**
     * A magnet built from an info hash, for the indexers that send only that.
     *
     * No trackers: an info hash alone is enough for a DHT lookup, which is what the torrent server
     * does anyway. Adding the public tracker list some clients hardcode would be this app deciding
     * which trackers somebody's traffic goes to, which is not its decision to make.
     */
    fun magnetFor(infoHash: String, title: String): String =
        "$MAGNET_SCHEME?xt=urn:btih:$infoHash&dn=" + title.take(MAGNET_NAME_LIMIT).encodeForMagnet()

    /**
     * What has to survive a restart: the magnet, and the date the row shows.
     *
     * Not the whole release. The title is already the entry's title and the size and seeders are
     * already in its description, so storing them again would be two copies of the same facts that
     * can disagree — and a seeder count was true when the search ran and is not worth preserving.
     */
    fun memoOf(release: TorznabRelease): JsonObject = JsonObject(
        buildMap {
            put(MEMO_MAGNET, JsonPrimitive(release.magnet))
            if (release.publishedAt > 0) put(MEMO_PUBLISHED, JsonPrimitive(release.publishedAt))
        },
    )

    fun magnetIn(memo: JsonObject): String? =
        runCatching { memo[MEMO_MAGNET]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    fun publishedIn(memo: JsonObject): Long =
        runCatching { memo[MEMO_PUBLISHED]?.jsonPrimitive?.long }.getOrNull() ?: 0L

    /** The hash out of a magnet, if it is spelled the ordinary way. */
    fun infoHashOf(magnet: String): String? =
        INFO_HASH.find(magnet)?.groupValues?.getOrNull(1)?.lowercase()

    /**
     * An RSS date, or zero.
     *
     * Zero rather than a throw: the row shows a date only when it has one, so an unparseable date
     * costs that row its date and nothing else. RFC 822 is what the spec says and what most
     * indexers send; the ones that send ISO-8601 instead are why this tries both rather than
     * insisting.
     */
    private fun parseRfc822(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0L
        DATE_FORMATS.forEach { format ->
            runCatching {
                return java.time.ZonedDateTime.parse(text, format).toInstant().toEpochMilli()
            }
        }
        return runCatching { java.time.Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
    }

    private fun String.encodeForMagnet(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private const val MEMO_MAGNET = "animato.torznab.magnet"
    private const val MEMO_PUBLISHED = "animato.torznab.published"

    private const val MAGNET_SCHEME = "magnet:"
    private const val MAGNET_NAME_LIMIT = 80

    private val INFO_HASH = Regex("""xt=urn:btih:([A-Za-z0-9]+)""")

    private val DATE_FORMATS = listOf(
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
        java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )
}
