package eu.kanade.tachiyomi.animeextension.ar.example

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

/**
 * One website, seen as one source.
 *
 * ## The shape of the job
 *
 * Every method below is one of two kinds: a `*Request` that says which page to fetch, and a
 * `*Parse` that turns the response into the app's model. The app calls them in pairs and does the
 * fetching itself, which is why nothing here opens a connection — that is what gives the app its
 * cache, its Cloudflare handling and its proxy for free.
 *
 * ## What actually takes the time
 *
 * The three listing methods are usually an afternoon: find the rows, read a title, a link and an
 * image out of each. [videoListParse] is the rest of the week. Almost no site puts an `.mp4` in its
 * HTML — the page holds an iframe pointing at an embed host, that page holds obfuscated JavaScript,
 * and the real link is what the script builds. Every host is its own small problem, and one site
 * usually has several.
 *
 * ## Before writing any of it
 *
 * Open the site in a desktop browser with the network tab recording and play something. What you
 * are looking for is the last request before the video starts — that is the URL this class has to
 * end up producing, and everything else here is working backwards from it. If that request carries
 * a `Referer` or a cookie, so must yours: see [headersBuilder].
 */
class ExampleSource : AnimeHttpSource() {

    override val name = "Example"

    override val baseUrl = "https://example.com"

    /** Two letters, and it decides whether anybody with a language filter ever sees this. */
    override val lang = "ar"

    override val supportsLatest = true

    /**
     * Headers sent with every request the app makes for this source.
     *
     * The commonest reason a source that works in a browser returns nothing here: the site checks
     * `Referer`, or refuses a client that does not look like a browser. Add what the network tab
     * shows, and nothing more.
     */
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ---------------------------------------------------------------- popular

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/popular?page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = Jsoup.parse(response.body.string(), baseUrl)

        // TODO: the selector for one card in the grid.
        val entries = document.select("div.card").map { element ->
            SAnime.create().apply {
                // `url` is what identifies this entry forever — it is stored in the library and
                // handed back to `animeDetailsParse` and `episodeListParse` later. Keep it
                // relative and keep it stable; a URL that changes shape between releases orphans
                // everything anybody had saved.
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                title = element.selectFirst("h3")?.text().orEmpty()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        // False stops the grid asking for more. Getting this wrong in the other direction is an
        // infinite scroll that requests the same page forever.
        val hasNext = document.selectFirst("a.next") != null
        return AnimesPage(entries, hasNext)
    }

    // ---------------------------------------------------------------- latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ---------------------------------------------------------------- search

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        GET("$baseUrl/search?q=${query.trim()}&page=$page", headers)

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    // ---------------------------------------------------------------- details

    override fun animeDetailsParse(response: Response): SAnime {
        val document = Jsoup.parse(response.body.string(), baseUrl)

        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text().orEmpty()
            description = document.selectFirst("div.synopsis")?.text()
            genre = document.select("a.genre").joinToString { it.text() }
            author = document.selectFirst("span.studio")?.text()
            thumbnail_url = document.selectFirst("img.poster")?.absUrl("src")
            // SAnime.ONGOING, COMPLETED, or UNKNOWN. Read it off the page rather than guessing —
            // it is what decides whether the library keeps checking this entry for new episodes.
            status = SAnime.UNKNOWN
        }
    }

    // ---------------------------------------------------------------- episodes

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = Jsoup.parse(response.body.string(), baseUrl)

        return document.select("li.episode").map { element ->
            SEpisode.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                name = element.selectFirst("span.title")?.text().orEmpty()
                // The number is what the app sorts and tracks by, so it has to be right even when
                // the site's own ordering is not. A missing one leaves every episode at 0 and the
                // list in whatever order the page happened to be in.
                episode_number = element.attr("data-number").toFloatOrNull() ?: 0f
            }
        }
            // Newest first is what the app expects; a site listing oldest-first needs this.
            .reversed()
    }

    // ---------------------------------------------------------------- video

    /**
     * The part that is actually hard.
     *
     * What comes back here must be a URL the player can open directly — an `.mp4`, an `.m3u8`, or
     * a magnet. An embed page is not one, so a source that returns iframe URLs plays nothing.
     *
     * The usual shape: read the embed URLs out of this page, fetch each one, and pull the real
     * link out of whatever that host does. Each host is its own extractor and they are the bulk of
     * the work; write one per host rather than one large branch.
     *
     * Return every quality you find. The app remembers which one somebody picked per anime, so
     * offering four is more useful than picking one on their behalf.
     */
    override fun videoListParse(response: Response): List<Video> {
        val document = Jsoup.parse(response.body.string(), baseUrl)

        val embeds = document.select("iframe").mapNotNull { it.absUrl("src").takeIf(String::isNotBlank) }

        return embeds.flatMap { embedUrl ->
            // TODO: one extractor per host. Fetch `embedUrl` with `client`, find the real link,
            //  and return it as a Video. Give the quality a name people recognise ("1080p"), and
            //  add whatever headers that host needs to play — usually a Referer of its own.
            extractFrom(embedUrl)
        }
    }

    private fun extractFrom(embedUrl: String): List<Video> {
        // Deliberately empty. This is the site-specific half, and it cannot be templated: what
        // goes here depends entirely on what the network tab showed you.
        return emptyList()
    }
}
