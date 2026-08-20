package animato.anime.jellyfin

import animato.anime.content.BrowsableByCategory
import animato.anime.content.EntryForm
import animato.anime.content.KnowsEntryForm
import animato.anime.content.SourceCategory
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Headers
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * One Jellyfin or Emby server, seen as one source.
 *
 * ## What makes this different from the other two
 *
 * A Stremio addon is a stranger's website and an M3U playlist is a file. This is the user's own
 * machine, and that changes three things.
 *
 * **Everything needs a token.** There is no anonymous request, so the source cannot exist before a
 * sign-in — which is why adding a server is signing in to it, and why the store holds a token
 * rather than an address.
 *
 * **The covers need it too.** A Jellyfin image URL carries the token in the query string rather
 * than in a header, because the image loader fetches it without going through this class at all.
 * Same for the video: it is handed to mpv, to the downloader and to a cast target, and none of
 * those would carry headers attached here.
 *
 * **Paging is real.** A personal library is thousands of items and the server pages properly, with
 * a total count in every response — so unlike the Stremio side there is no guessing at whether
 * there is a next page.
 */
class JellyfinSource(
    val server: JellyfinServer,
) : AnimeHttpSource(), KnowsEntryForm, BrowsableByCategory {

    private val json: Json by injectLazy()

    private val store: JellyfinServerStore by injectLazy()

    override val baseUrl: String = server.url

    /**
     * The account, and the machine.
     *
     * Two accounts on one server are two sources with two libraries, which is what they are — and a
     * row reading only the host would be the same word twice for somebody who signed in as
     * themselves and as their child.
     */
    override val name: String = "${server.name} (${hostLabel()})"

    /** A personal library is whatever its owner put in it, which is rarely one language. */
    override val lang: String = MULTI_LANG

    /**
     * Identity is the address and the account.
     *
     * Not the name: Jellyfin's account names are editable, and a source whose id follows a name
     * takes the whole library with it the first time somebody tidies theirs up.
     */
    override val id: Long by lazy { idFor(server.url, server.userId) }

    /**
     * Recently added, which a server genuinely knows.
     *
     * This is the one shelf that means more here than anywhere else in the app: on a personal
     * server, *what arrived lately* is what somebody actually opens the app to see.
     */
    override val supportsLatest: Boolean = true

    override fun formOf(entryUrl: String): EntryForm = JellyfinMapper.formOf(entryUrl)

    override suspend fun categories(): List<SourceCategory> {
        val views = requestItems(JellyfinUrls.views(server.url, server.userId))
        return JellyfinMapper.videoLibraries(views.items)
            .map { SourceCategory(id = it.id, label = it.name) }
    }

    override suspend fun browseCategory(categoryId: String, page: Int): AnimesPage = fetch(
        JellyfinUrls.items(
            server.url,
            server.userId,
            parentId = categoryId,
            startIndex = offsetOf(page),
            limit = PAGE_SIZE,
        ),
        page,
    )

    /**
     * No filter sheet, deliberately.
     *
     * The only thing there is to narrow by is the library, and that arrives through
     * [categories] as the chip row above the grid — which is one tap rather than four, and is the
     * whole reason `BrowsableByCategory` exists. A sheet holding one dropdown that duplicates a
     * visible row is a control that can only ever disagree with it.
     */
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override suspend fun getPopularAnime(page: Int): AnimesPage = fetch(
        JellyfinUrls.items(server.url, server.userId, startIndex = offsetOf(page), limit = PAGE_SIZE),
        page,
    )

    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetch(
        JellyfinUrls.items(
            server.url,
            server.userId,
            startIndex = offsetOf(page),
            limit = PAGE_SIZE,
            sortBy = JellyfinUrls.SORT_DATE_CREATED,
            descending = true,
        ),
        page,
    )

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage = fetch(
        JellyfinUrls.items(
            server.url,
            server.userId,
            search = query.trim().takeIf { it.isNotEmpty() },
            startIndex = offsetOf(page),
            limit = PAGE_SIZE,
        ),
        page,
    )

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val (_, itemId) = JellyfinMapper.parseEntryUrl(anime.url) ?: return anime
        val item = request<JellyfinItem>(JellyfinUrls.item(server.url, server.userId, itemId))
        return JellyfinMapper.toSAnime(item, server.url).apply { initialized = true }
    }

    /**
     * The episodes, or the one row a film has.
     *
     * A film is not asked about at all: `/Shows/{id}/Episodes` is meaningful only for a series, and
     * asking it about a film returns an empty list — which on screen is a film that looks like a
     * series whose episodes failed to load.
     */
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val (type, itemId) = JellyfinMapper.parseEntryUrl(anime.url) ?: return emptyList()
        if (type == JellyfinMapper.TYPE_MOVIE) {
            val item = request<JellyfinItem>(JellyfinUrls.item(server.url, server.userId, itemId))
            return listOf(JellyfinMapper.toSingleEpisode(item))
        }
        val episodes = requestItems(JellyfinUrls.episodes(server.url, server.userId, itemId))
        return JellyfinMapper.toEpisodes(episodes.items)
    }

    /**
     * The file, as the server stores it.
     *
     * One hoster with one video, and no quality picker — a server holds one file per episode, and
     * a chooser over a list of one is a dialog that exists to be dismissed. Somebody who keeps two
     * cuts of a film has two items on their server, which is their decision and is left as they
     * made it.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val (_, itemId) = JellyfinMapper.parseEntryUrl(episode.url) ?: return emptyList()
        val url = JellyfinUrls.stream(server.url, itemId, server.token)
        return listOf(
            Hoster(
                hosterUrl = url,
                hosterName = server.name,
                videoList = listOf(Video(videoUrl = url, videoTitle = episode.name)),
            ),
        )
    }

    override fun getAnimeUrl(anime: SAnime): String {
        val id = JellyfinMapper.parseEntryUrl(anime.url)?.second ?: return baseUrl
        return JellyfinUrls.webPage(server.url, id, server.serverId)
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        val id = JellyfinMapper.parseEntryUrl(episode.url)?.second ?: return baseUrl
        return JellyfinUrls.webPage(server.url, id, server.serverId)
    }

    /**
     * One page, and whether there is another.
     *
     * Answered from the total rather than guessed from a full page, which is the luxury of talking
     * to a real API: `TotalRecordCount` ignores the page, so *is there more* is arithmetic instead
     * of the "a full page probably means keep going" convention the Stremio side has to use.
     */
    private suspend fun fetch(url: String, page: Int): AnimesPage {
        val response = requestItems(url)
        val items = response.items.map { JellyfinMapper.toSAnime(it, server.url) }
        return AnimesPage(items, hasNextPage = offsetOf(page) + response.items.size < response.totalRecordCount)
    }

    private suspend fun requestItems(url: String): JellyfinItems = request(url)

    private suspend inline fun <reified T> request(url: String): T {
        val response = client.newCall(GET(url, jellyfinHeaders())).awaitSuccess()
        return with(json) { response.parseAs<T>() }
    }

    private fun jellyfinHeaders(): Headers = store.headers(server)

    private fun offsetOf(page: Int): Int = (page - 1).coerceAtLeast(0) * PAGE_SIZE

    private fun hostLabel(): String =
        runCatching { java.net.URI(server.url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: server.url

    companion object {
        /**
         * A stable id for a server and an account.
         *
         * A different prefix from the Stremio and M3U ones, so a machine that serves a playlist and
         * a Jellyfin instance at the same address is two sources rather than one overwriting the
         * other.
         */
        fun idFor(serverUrl: String, userId: String): Long {
            val key = "jellyfin/${serverUrl.trim().trimEnd('/').lowercase()}/$userId"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                .reduce(Long::or) and Long.MAX_VALUE
        }

        private const val MULTI_LANG = "all"
        private const val PAGE_SIZE = 60
    }
}
