package eu.kanade.tachiyomi.animeextension.ar.shahedpro

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response

/**
 * An empty module, ready to be filled in.
 *
 * Everything here is a stub that throws. Nothing has been implemented, and nothing has been
 * inspected — the selectors, the URL shapes and the video extraction are all to be worked out
 * against the live site, which is what GUIDE.md walks through.
 *
 * The three things that are already right are the ones that are easy to get wrong and produce an
 * extension that installs and is never seen: the feature declaration, the class metadata and the
 * lib version, all in AndroidManifest.xml.
 *
 * See ExampleSource in the sibling module for the same skeleton with a worked-through shape for
 * each method and notes on what belongs in it.
 */
class ShahedProSource : AnimeHttpSource() {

    override val name = "ShahedPro"

    override val baseUrl = "https://www.shahedpro.com"

    override val lang = "ar"

    override val supportsLatest = true

    /**
     * Many sites answer differently, or not at all, without a Referer or a browser-shaped
     * User-Agent. If a page works in a browser and returns nothing here, this is the first place
     * to look — put in what the network tab shows and nothing more.
     */
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ---------------------------------------------------------------- popular

    override fun popularAnimeRequest(page: Int): Request =
        GET(baseUrl, headers)

    override fun popularAnimeParse(response: Response): AnimesPage =
        TODO("Find the cards in the listing and read a title, a link and an image out of each")

    // ---------------------------------------------------------------- latest

    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): AnimesPage =
        TODO("Usually the same shape as popularAnimeParse, on a different page")

    // ---------------------------------------------------------------- search

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        TODO("The search URL the site's own search box produces")

    override fun searchAnimeParse(response: Response): AnimesPage =
        TODO("Usually the same shape as popularAnimeParse")

    // ---------------------------------------------------------------- details

    override fun animeDetailsParse(response: Response): SAnime =
        TODO("Title, description, genres, poster, and status")

    // ---------------------------------------------------------------- episodes

    override fun episodeListParse(response: Response): List<SEpisode> =
        TODO("One SEpisode per row, newest first, each with a real episode_number")

    // ---------------------------------------------------------------- video

    /**
     * The part that is actually the work.
     *
     * What this returns has to be a URL the player can open directly — an `.mp4`, an `.m3u8` or a
     * magnet. An embed page is not one, so returning iframe URLs plays nothing.
     */
    override fun videoListParse(response: Response): List<Video> =
        TODO("Resolve whatever the page embeds down to direct video URLs, one Video per quality")
}
