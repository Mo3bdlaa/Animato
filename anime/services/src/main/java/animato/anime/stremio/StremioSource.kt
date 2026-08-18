package animato.anime.stremio

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Hoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest

/**
 * One Stremio addon, seen as one source.
 *
 * This is the first source in the app that is not an extension. Nothing was downloaded and
 * nothing runs in our process — the addon is a URL that answers JSON, and everything below is
 * ordinary HTTP against it. That difference is the entire point: an addon cannot crash us, cannot
 * read our storage, and does not stop working because a shrinker removed a method it needed.
 *
 * It extends [AnimeHttpSource] rather than implementing the catalogue interface directly for one
 * concrete reason: global search collects sources through `getOnlineSources()`, which filters by
 * this exact type, and a source you cannot search across the app is a source most people never
 * find. Only `baseUrl` is required of us; the request/parse scaffolding underneath is for HTML
 * scraping and is left untouched, with the suspending entry points overridden instead.
 *
 * ### Catalogs and our two shelves
 *
 * An addon publishes any number of catalogs; we have exactly two shelves, Popular and Latest. The
 * mapping picks by name where a name says what it is ("Top", "Popular" / "New", "Recent") and
 * falls back to first and second. Every other catalog is still reachable — the filter sheet lists
 * them all, which is how the sources with a dozen catalogs stay usable without inventing a third
 * shelf nobody asked for.
 */
class StremioSource(
    val addon: StremioAddon,
) : AnimeHttpSource() {

    private val json: Json by injectLazy()

    override val baseUrl: String = addon.url

    override val name: String = addon.manifest.name.takeIf { it.isNotBlank() } ?: addon.url

    /**
     * Addons are not language-scoped the way scraper extensions are — one addon usually serves
     * whatever the underlying catalogue holds — so they sit under the multi-language heading
     * rather than claiming a language they do not have.
     */
    override val lang: String = MULTI_LANG

    /**
     * Identity is the addon's URL, not its name.
     *
     * [AnimeHttpSource] derives ids from the name, which is the wrong anchor here: an addon can
     * rename itself in a manifest refresh, and a source whose id moves takes everybody's library
     * entries with it. The URL is what the user actually added, configuration path included, so
     * two differently-configured installs of the same addon stay two distinct sources.
     */
    override val id: Long by lazy { idFor(addon.url) }

    override val supportsLatest: Boolean by lazy { latestCatalog() != null && latestCatalog() != popularCatalog() }

    private val catalogs: List<StremioCatalog> get() = addon.manifest.catalogs

    override fun getFilterList(): AnimeFilterList {
        if (catalogs.isEmpty()) return AnimeFilterList()
        val filters = mutableListOf<AnimeFilter<*>>()
        if (catalogs.size > 1) {
            filters += CatalogFilter(catalogs.map { it.displayName })
        }
        catalogs.forEach { catalog ->
            val options = catalog.optionsFor(EXTRA_GENRE)
            if (options.isNotEmpty()) {
                filters += GenreFilter(catalog.displayName, options)
            }
        }
        return AnimeFilterList(filters)
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val catalog = popularCatalog() ?: return AnimesPage(emptyList(), false)
        return fetchCatalog(catalog, page, query = "", genre = null)
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val catalog = latestCatalog() ?: return AnimesPage(emptyList(), false)
        return fetchCatalog(catalog, page, query = "", genre = null)
    }

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val chosen = filters.filterIsInstance<CatalogFilter>().firstOrNull()
            ?.let { catalogs.getOrNull(it.state) }
        // A blank query is the filter sheet being used as a catalogue picker, so any catalog will
        // do. A real query needs a catalog that accepts one, and the chosen catalog often is not
        // it — addons routinely publish browsing catalogs and a separate search catalog.
        val catalog = when {
            query.isBlank() -> chosen ?: popularCatalog()
            chosen?.supports(EXTRA_SEARCH) == true -> chosen
            else -> catalogs.firstOrNull { it.supports(EXTRA_SEARCH) }
        } ?: return AnimesPage(emptyList(), false)

        val genre = filters.filterIsInstance<GenreFilter>()
            .firstOrNull { it.name == catalog.displayName }
            ?.selected()

        return fetchCatalog(catalog, page, query, genre)
    }

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate {
        if (!fetchDetails && !fetchEpisodes) return SAnimeEpisodeUpdate(anime, episodes)
        val (type, id) = StremioMapper.parseEntryUrl(anime.url)
            ?: return SAnimeEpisodeUpdate(anime, episodes)
        // Details and episodes come out of the same document, so both flags cost one request.
        val meta = requestMeta(type, id) ?: return SAnimeEpisodeUpdate(anime, episodes)
        return SAnimeEpisodeUpdate(
            anime = if (fetchDetails) StremioMapper.toSAnime(meta, type) else anime,
            episodes = if (fetchEpisodes) StremioMapper.toEpisodes(meta, type) else episodes,
        )
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val (type, id) = StremioMapper.parseEntryUrl(anime.url) ?: return anime
        val meta = requestMeta(type, id) ?: return anime
        return StremioMapper.toSAnime(meta, type)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val (type, id) = StremioMapper.parseEntryUrl(anime.url) ?: return emptyList()
        val meta = requestMeta(type, id) ?: return emptyList()
        return StremioMapper.toEpisodes(meta, type)
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val (type, id) = StremioMapper.parseEntryUrl(episode.url) ?: return emptyList()
        if (!addon.manifest.serves(RESOURCE_STREAM)) return emptyList()
        val response = client.newCall(GET(StremioUrls.stream(baseUrl, type, id), headers))
            .awaitSuccess()
        val streams = with(json) { response.parseAs<StremioStreamResponse>() }.streams
        val videos = StremioMapper.toVideos(streams)
        return if (videos.isEmpty()) emptyList() else videos.toHosterList()
    }

    /**
     * Streams arrive complete from the one `/stream` call, so a hoster is already carrying its
     * videos by the time the player asks for them and there is nothing left to fetch.
     */
    override suspend fun getVideoList(hoster: Hoster): List<Video> = hoster.videoList.orEmpty()

    /**
     * Where "open in browser" should land.
     *
     * The addon's own JSON endpoint is not a page anyone wants to look at. Stremio's ids are
     * overwhelmingly IMDb ids, and for those there is a real page — so send people there, and
     * fall back to the addon itself when the id belongs to some other catalogue.
     */
    override fun getAnimeUrl(anime: SAnime): String {
        val id = StremioMapper.parseEntryUrl(anime.url)?.second ?: return baseUrl
        return if (id.startsWith(IMDB_PREFIX)) "$IMDB_TITLE_URL${id.substringBefore(':')}/" else baseUrl
    }

    override fun getEpisodeUrl(episode: SEpisode): String {
        val id = StremioMapper.parseEntryUrl(episode.url)?.second ?: return baseUrl
        return if (id.startsWith(IMDB_PREFIX)) "$IMDB_TITLE_URL${id.substringBefore(':')}/" else baseUrl
    }

    private suspend fun requestMeta(type: String, id: String): StremioMeta? {
        if (!addon.manifest.serves(RESOURCE_META)) return null
        val response = client.newCall(GET(StremioUrls.meta(baseUrl, type, id), headers)).awaitSuccess()
        return with(json) { response.parseAs<StremioMetaResponse>() }.meta
    }

    private suspend fun fetchCatalog(
        catalog: StremioCatalog,
        page: Int,
        query: String,
        genre: String?,
    ): AnimesPage {
        val extra = buildMap {
            if (query.isNotBlank() && catalog.supports(EXTRA_SEARCH)) put(EXTRA_SEARCH, query)
            // A catalog may refuse to answer at all without a genre. Picking its first option is
            // better than sending nothing and rendering the addon as empty and broken.
            val effectiveGenre = genre ?: catalog.takeIf { it.requires(EXTRA_GENRE) }
                ?.optionsFor(EXTRA_GENRE)?.firstOrNull()
            if (!effectiveGenre.isNullOrBlank()) put(EXTRA_GENRE, effectiveGenre)
            if (page > 1) put(EXTRA_SKIP, ((page - 1) * PAGE_SIZE).toString())
        }

        val url = StremioUrls.catalog(baseUrl, catalog.type, catalog.id, extra)
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        val metas = with(json) { response.parseAs<StremioCatalogResponse>() }.metas
        return AnimesPage(
            animes = metas.map { StremioMapper.toSAnime(it, catalog.type) },
            // Stremio has no "there is more" flag; the convention is a full page means keep going.
            // Erring towards stopping is deliberate — a page that is never the last one leaves the
            // grid loading forever against an addon that simply returns the same items.
            hasNextPage = metas.size >= PAGE_SIZE,
        )
    }

    /** Catalogs that can be browsed without a query — a search-only catalog answers nothing without one. */
    private fun browsableCatalogs(): List<StremioCatalog> = catalogs.filterNot { it.requires(EXTRA_SEARCH) }

    private fun popularCatalog(): StremioCatalog? {
        val browsable = browsableCatalogs()
        return browsable.firstOrNull { catalog -> POPULAR_WORDS.any { catalog.displayName.contains(it, true) } }
            ?: browsable.firstOrNull()
    }

    private fun latestCatalog(): StremioCatalog? {
        val browsable = browsableCatalogs()
        return browsable.firstOrNull { catalog -> LATEST_WORDS.any { catalog.displayName.contains(it, true) } }
            ?: browsable.getOrNull(1)
    }

    private class CatalogFilter(names: List<String>) :
        AnimeFilter.Select<String>(FILTER_CATALOG, names.toTypedArray())

    private class GenreFilter(catalogName: String, options: List<String>) :
        AnimeFilter.Select<String>(catalogName, (listOf(ANY_GENRE) + options).toTypedArray()) {
        fun selected(): String? = values.getOrNull(state)?.takeIf { it != ANY_GENRE }
    }

    companion object {
        const val MULTI_LANG = "all"

        /**
         * The source id for an addon URL, stable for as long as that URL is.
         *
         * The same MD5-of-a-key shape [AnimeHttpSource] uses, over a namespaced key so an addon
         * can never land on the id of an extension that happens to be named after it. The sign
         * bit is cleared for the same reason it is there: ids are stored as signed longs and
         * negative ones are reserved for the app's own sources.
         */
        fun idFor(addonUrl: String): Long {
            val key = "stremio/${StremioUrls.normalizeBase(addonUrl).lowercase()}"
            val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                .reduce(Long::or) and Long.MAX_VALUE
        }

        private const val PAGE_SIZE = 100
        private const val EXTRA_SEARCH = "search"
        private const val EXTRA_GENRE = "genre"
        private const val EXTRA_SKIP = "skip"
        private const val RESOURCE_META = "meta"
        private const val RESOURCE_STREAM = "stream"
        private const val FILTER_CATALOG = "Catalog"
        private const val ANY_GENRE = "Any"
        private const val IMDB_PREFIX = "tt"
        private const val IMDB_TITLE_URL = "https://www.imdb.com/title/"

        private val POPULAR_WORDS = listOf("popular", "top", "trending", "featured")
        private val LATEST_WORDS = listOf("latest", "new", "recent", "airing", "updated")
    }
}
