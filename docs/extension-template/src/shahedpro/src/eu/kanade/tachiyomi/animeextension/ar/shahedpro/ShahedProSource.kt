package eu.kanade.tachiyomi.animeextension.ar.shahedpro

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * shahedpro.com — a WordPress site with a theme of its own, serving films and series.
 *
 * ## How the site is put together
 *
 * Everything that lists things — the section pages, the search — renders the same card, so one
 * parser covers all of them. A film and a series differ only in what their page contains: a series
 * has a grid of episode links, a film has none and is played from its own page.
 *
 * The player is not on the entry's page. Every film and episode has a `watch/` child page, and that
 * is where the servers are.
 *
 * ## How far this gets, and what is left
 *
 * The listing, details and episode parsing is written against the site's real markup. The video
 * path goes: episode page -> its `watch/` child -> the servers, base64-encoded in `data-enc` ->
 * fetch the embed -> unpack -> the HLS playlist.
 *
 * That last stretch was checked end to end against morencius, which turned out to be a VidHide
 * clone — it says so in its own player config — serving a jwplayer setup inside a Dean Edwards
 * packed script. The whole family packs the same way, so [unpackIfPacked] is written once and is
 * not host-specific.
 *
 * The servers seen while writing this were mivalyo, kravaxxa, listeamed, doodstream (d-s.io),
 * morencius and hgcloud; the set differs per entry and changes over time. Only morencius was
 * confirmed working. The others were not reachable from where this was written, so whether the
 * unpacker is enough for them is unknown rather than ruled out — d-s.io in particular is
 * doodstream, which hands its URL out over a separate request and will need a branch in [videos].
 */
class ShahedProSource : AnimeHttpSource() {

    override val name = "ShahedPro"

    override val baseUrl = "https://www.shahedpro.com"

    override val lang = "ar"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ---------------------------------------------------------------- listings

    /*
     * The site has no popularity ordering to read, so the two listings are its two sections, both
     * newest-first: series under "popular", films under "latest". Neither is a ranking, and this is
     * the closest honest mapping onto what the app asks for.
     */

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/tvshows/${pagePath(page)}", headers)

    override fun popularAnimeParse(response: Response): AnimesPage = listingParse(response)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/movies/${pagePath(page)}", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = listingParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // Built rather than concatenated: search terms are Arabic, and one containing `&` or `#`
        // would otherwise end up as a second query parameter instead of part of the term.
        val url = "$baseUrl/${pagePath(page)}".toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim())
            .build()
        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listingParse(response)

    /** Page 1 has no segment of its own; every later page is `page/N/`. Search appends `?s=`. */
    private fun pagePath(page: Int): String = if (page > 1) "page/$page/" else ""

    private fun listingParse(response: Response): AnimesPage {
        val document = response.asDocument()
        val entries = document.select("article.media-card").mapNotNull { it.toSAnimeOrNull() }
        return AnimesPage(entries, document.hasNextPage())
    }

    private fun Element.toSAnimeOrNull(): SAnime? {
        val card = this
        val link = card.selectFirst("a.mc2-poster-link") ?: return null
        val href = link.attr("href").takeIf { it.isNotBlank() } ?: return null

        // The visible caption is the short title; the link's aria-label is the full one, and is
        // what is left when a card renders without its caption.
        val caption = card.selectFirst(".mc2-ht-title")?.text()?.trim()

        return SAnime.create().apply {
            setUrlWithoutDomain(href)
            title = caption?.takeIf { it.isNotEmpty() } ?: link.attr("aria-label")
            thumbnail_url = card.selectFirst("img.mc2-img")?.absUrl("src")
        }
    }

    /**
     * Whether the grid should ask for another page.
     *
     * Read off the pagination's own numbers rather than the `<link rel="next">` in the head, which
     * this site still emits one page past the end — following it lands on a page with no cards at
     * all. Comparing the numbers stops on the last real page instead.
     */
    private fun Document.hasNextPage(): Boolean {
        val links = select("a.page-link")
        val current = links.firstOrNull { it.hasClass("current") }?.pageNumber() ?: return false
        return links.any { it.pageNumber() > current }
    }

    private fun Element.pageNumber(): Int =
        PAGE_NUMBER.find(attr("href"))?.groupValues?.get(1)?.toIntOrNull() ?: 1

    // ---------------------------------------------------------------- details

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asDocument()

        return SAnime.create().apply {
            title = document.selectFirst("h1.sp-title")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(".sp-poster-wrap img")?.absUrl("src")
            genre = document.select("a.sp-genre-tag").joinToString { it.text() }
            description = document.overview()
            author = document.infoValue("المخرج")
            artist = document.infoValue("الاستوديو")
            // A film is finished by definition. A series' page says how many seasons it has but
            // never whether it is still running, so claiming either way would be inventing it.
            status = if (response.request.url.encodedPath.startsWith("/movies/")) {
                SAnime.COMPLETED
            } else {
                SAnime.UNKNOWN
            }
        }
    }

    /**
     * The synopsis, which the page holds twice: a truncated copy and the full one, hidden, each
     * carrying the button that swaps them. The full one is the one worth having, and the button's
     * own label has to come back out of it.
     */
    private fun Document.overview(): String? {
        val block = selectFirst("#spOf") ?: selectFirst("#spOs") ?: return null
        return block.clone()
            .also { it.select("button").remove() }
            .text()
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /** One row of the info panel, found by the label beside it. */
    private fun Document.infoValue(label: String): String? =
        select("div.sp-ic")
            .firstOrNull { it.selectFirst(".sp-ic-label")?.text()?.trim() == label }
            ?.selectFirst(".sp-ic-val")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    // ---------------------------------------------------------------- episodes

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asDocument()
        val grids = document.select("div.sp-episodes-grid")

        // A film has no episode grid. It is played from its own page, so the page becomes the one
        // episode the app needs in order to have something to open.
        if (grids.isEmpty()) {
            return listOf(
                SEpisode.create().apply {
                    setUrlWithoutDomain(response.request.url.toString())
                    name = "الفيلم"
                    episode_number = 1f
                },
            )
        }

        // Most entries are a single season — including the ones that are season 3 on their own, in
        // a grid still called ss3. Several grids in one entry is the case worth handling specially.
        val multipleSeasons = grids.size > 1

        return grids.flatMap { grid ->
            val season = SEASON_ID.find(grid.id())?.groupValues?.get(1)?.toIntOrNull() ?: 1
            grid.select("a.sp-ep-card").mapNotNull { it.toSEpisodeOrNull(season, multipleSeasons) }
        }.sortedByDescending { it.episode_number }
    }

    private fun Element.toSEpisodeOrNull(season: Int, multipleSeasons: Boolean): SEpisode? {
        val card = this
        val href = card.attr("href").takeIf { it.isNotBlank() } ?: return null
        val number = card.selectFirst(".sp-ep-num")?.text()?.trim()?.toFloatOrNull() ?: return null
        val label = card.selectFirst(".sp-ep-info")?.text()?.trim()?.takeIf { it.isNotEmpty() }

        return SEpisode.create().apply {
            setUrlWithoutDomain(href)
            name = (label ?: "الحلقة ${number.toInt()}")
                .let { if (multipleSeasons) "الموسم $season — $it" else it }
            /*
             * With one season the site's own numbering is used, which is what a tracker expects.
             *
             * With several it cannot be: every season restarts at 1, so a flat list would hold
             * three episodes numbered 1, sorting arbitrarily and confusing "next episode".
             * `season.episode` keeps them ordered and distinct, at the cost of being a number
             * nobody is tracking against — which is why the season is also spelled out in the name.
             */
            episode_number = if (multipleSeasons) season + number / 100f else number
        }
    }

    // ---------------------------------------------------------------- video

    /** The player lives on a `watch/` page of its own, not on the episode's page. */
    override fun videoListRequest(episode: SEpisode): Request =
        GET(baseUrl + episode.url.removeSuffix("/") + "/watch/", headers)

    /**
     * The servers, as the site stores them.
     *
     * Each button under the player carries its embed URL base64-encoded in `data-enc`, and the page
     * only decodes one when somebody clicks it — which is why the HTML holds a single iframe and
     * four servers. Decoding them here gets the same list the site would.
     *
     * What [videos] returns has to be a URL the player can open — an `.mp4`, an `.m3u8` or a
     * magnet. An embed page is not one, so the extractors are where the remaining work is; see the
     * note on the class.
     */
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asDocument()
        val watchUrl = response.request.url.toString()

        return document.select("button.wSrvBtn[data-enc]")
            .mapNotNull { it.toServerOrNull() }
            // One host being down, blocked or rewritten should cost its own entry and no more. The
            // others are still playable, and letting it throw here would lose all of them.
            .flatMap { server -> runCatching { server.videos(watchUrl) }.getOrDefault(emptyList()) }
    }

    /** `label` rather than `name`, which on a source already means the source's own name. */
    private data class Server(val label: String, val embedUrl: String)

    private fun Element.toServerOrNull(): Server? {
        val embedUrl = decodeBase64(attr("data-enc"))
            ?.takeIf { it.startsWith("http") }
            ?: return null
        // The button's own text is the host's name; the play glyph beside it is a child element,
        // which is what ownText leaves out.
        val label = ownText().trim().takeIf { it.isNotEmpty() }
            ?: embedUrl.toHttpUrlOrNull()?.host.orEmpty()
        return Server(label, embedUrl)
    }

    private fun decodeBase64(value: String): String? =
        runCatching { String(Base64.decode(value, Base64.DEFAULT)) }.getOrNull()

    private fun Server.videos(watchUrl: String): List<Video> {
        // Host-specific extractors belong here, dispatched on this server's host, for the ones the
        // scan below cannot reach. It gets further than it looks — see its note.
        return genericVideos(watchUrl)
    }

    /**
     * Fetch the embed page, undo the packing if it is packed, and take the media URL out of it.
     *
     * This is not the fallback it sounds like. The hosts this site uses are mostly VidHide clones —
     * morencius identifies itself as one in its own player config — and they all ship the same
     * shape: a jwplayer setup inside a Dean Edwards packed script, holding an HLS master playlist.
     * Unpacking is what turns "nothing in the page" into that URL, and it is host-agnostic, so one
     * implementation covers the family rather than one host.
     *
     * What it does not cover is a host that encrypts rather than packs, or one that hands the URL
     * out over a separate request. Those need [videos] to grow a branch.
     */
    private fun Server.genericVideos(watchUrl: String): List<Video> {
        val embedHeaders = headers.newBuilder()
            .set("Referer", watchUrl)
            .build()

        val body = client.newCall(GET(embedUrl, embedHeaders)).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body.string()
        }
        val page = unpackIfPacked(body)

        // Playback goes back to the host rather than to shahedpro, and most of them check it.
        val playbackHeaders = embedUrl.toHttpUrlOrNull()?.let { embed ->
            headers.newBuilder()
                .set("Referer", "${embed.scheme}://${embed.host}/")
                .set("Origin", "${embed.scheme}://${embed.host}")
                .build()
        }

        return MEDIA_URL.findAll(page)
            .map { it.groupValues[1] }
            .distinct()
            .map { link ->
                Video(
                    url = embedUrl,
                    quality = "$label — ${link.qualityLabel()}",
                    videoUrl = link,
                    headers = playbackHeaders,
                )
            }
            .toList()
    }

    /** Whatever the URL admits to, so "auto" means "it did not say" rather than a guess. */
    private fun String.qualityLabel(): String =
        RESOLUTION.find(this)?.groupValues?.get(1)?.let { "${it}p" } ?: "auto"

    /**
     * Undo `eval(function(p,a,c,k,e,d){...})` — the Dean Edwards packer, which is what these hosts
     * put their player behind.
     *
     * The packed form is a payload with every recurring word replaced by its index written in
     * base-[radix], plus the dictionary to put back. Rebuilding it is a substitution and nothing
     * more: no JavaScript is run, which is the point — this has to work in an extension, not a
     * browser.
     *
     * Returns the input untouched when it is not packed, so it is safe to call on any page.
     */
    private fun unpackIfPacked(script: String): String {
        val match = PACKED.find(script) ?: return script
        val (payload, radixText, countText, dictionary) = match.destructured
        val radix = radixText.toIntOrNull()?.takeIf { it in 2..BASE_DIGITS.length } ?: return script
        val count = countText.toIntOrNull() ?: return script
        val words = dictionary.split("|")

        val lookup = HashMap<String, String>()
        for (index in 0 until count) {
            val word = words.getOrNull(index).orEmpty()
            // An empty slot means the token stands for itself and is left alone.
            if (word.isNotEmpty()) lookup[encodeBase(index, radix)] = word
        }

        val body = payload.replace("\\'", "'").replace("\\\\", "\\")
        return TOKEN.replace(body) { lookup[it.value] ?: it.value }
    }

    /** The packer's own numbering: base-[radix] over 0-9a-zA-Z, least significant digit last. */
    private fun encodeBase(value: Int, radix: Int): String {
        if (value == 0) return BASE_DIGITS.substring(0, 1)
        val digits = StringBuilder()
        var remaining = value
        while (remaining > 0) {
            digits.append(BASE_DIGITS[remaining % radix])
            remaining /= radix
        }
        return digits.reverse().toString()
    }

    // ---------------------------------------------------------------- shared

    private fun Response.asDocument(): Document = Jsoup.parse(body.string(), request.url.toString())

    companion object {
        private val PAGE_NUMBER = Regex("""/page/(\d+)""")
        private val SEASON_ID = Regex("""ss(\d+)""")
        private val MEDIA_URL = Regex("""(https?://[^"'\s\\<>]+\.(?:m3u8|mp4)[^"'\s\\<>]*)""")
        private val RESOLUTION = Regex("""(\d{3,4})[pP]""")

        private val PACKED = Regex(
            """\}\('(.*)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val TOKEN = Regex("""\b\w+\b""")
        private const val BASE_DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }
}
