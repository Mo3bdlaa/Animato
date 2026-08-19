package animato.anime.stremio

import android.app.Application
import aniyomi.core.common.torrent.TorrentPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
 * falls back to first and second.
 *
 * One shelf can be several catalogs, because catalogs are published *per content type* and a shelf
 * is not. Cinemeta publishes eight — Popular, New and Featured, each once for films and once for
 * series — and taking the first match gave shelves of films only. A shelf now takes the best match
 * for each type and interleaves them, so both are on the first screen.
 *
 * Every other catalog is still reachable: the filter sheet lists them all, which is how the addons
 * with a dozen catalogs stay usable without inventing a third shelf nobody asked for.
 */
class StremioSource(
    val addon: StremioAddon,
) : AnimeHttpSource() {

    private val json: Json by injectLazy()

    /**
     * Read at stream time rather than held: addons are added and removed while sources are alive,
     * and a title opened before a stream provider was installed must still find it afterwards.
     */
    private val addonStore: StremioAddonStore by injectLazy()

    private val torrentPreferences: TorrentPreferences by injectLazy()

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

    override val supportsLatest: Boolean by lazy {
        latestCatalogs().isNotEmpty() && latestCatalogs() != popularCatalogs()
    }

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

    override suspend fun getPopularAnime(page: Int): AnimesPage = fetchShelf(popularCatalogs(), page)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetchShelf(latestCatalogs(), page)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val chosen = filters.filterIsInstance<CatalogFilter>().firstOrNull()
            ?.let { catalogs.getOrNull(it.state) }
        // A blank query is the filter sheet being used as a catalogue picker, so any catalog will
        // do. A real query prefers a catalog that says it takes one — addons routinely publish
        // browsing catalogs and a separate search catalog — but falls back to asking anyway.
        //
        // That fallback is not defensive coding, it is Cinemeta. It answers
        // `/catalog/movie/top/search=spider.json` perfectly well while declaring no `search`
        // extra on any of its eight catalogs, and it is the catalogue almost everybody installs
        // first. Believing the manifest over the behaviour makes the default addon look like it
        // cannot search at all. An addon that really does ignore the extra returns its unfiltered
        // catalog, which is a worse answer than a filtered one but a far better answer than none.
        val catalog = when {
            query.isBlank() -> chosen ?: popularCatalog()
            chosen?.supports(EXTRA_SEARCH) == true -> chosen
            else -> catalogs.firstOrNull { it.supports(EXTRA_SEARCH) } ?: chosen ?: popularCatalog()
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
        // A season entry addresses the same document as its parent and takes a slice of it, so the
        // season is stripped off before the request and used to filter what comes back.
        val season = StremioMapper.parseSeasonUrl(anime.url)
        val (type, id) = StremioMapper.parseEntryUrl(season?.first ?: anime.url)
            ?: return SAnimeEpisodeUpdate(anime, episodes)
        // Details and episodes come out of the same document, so both flags cost one request.
        val meta = requestMeta(type, id) ?: return SAnimeEpisodeUpdate(anime, episodes)
        return SAnimeEpisodeUpdate(
            // A season keeps the name and number it was created with. Overwriting them from the
            // parent's document would rename every season of a series to the series.
            anime = if (fetchDetails && season == null) StremioMapper.toSAnime(meta, type) else anime,
            episodes = if (fetchEpisodes) {
                StremioMapper.toEpisodes(meta, type, onlySeason = season?.second)
            } else {
                episodes
            },
        )
    }

    /**
     * The seasons of a series, when it has more than one.
     *
     * Free, in a way it is nowhere else: Stremio's meta already carries every episode with its
     * season stamped on it, so the seasons fall out of the document the episode list was going to
     * need anyway. No second endpoint, no numbering scheme to infer from titles.
     */
    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate {
        if (!fetchDetails && !fetchSeasons) return SAnimeSeasonUpdate(anime, seasons)
        val (type, id) = StremioMapper.parseEntryUrl(anime.url)
            ?: return SAnimeSeasonUpdate(anime, seasons)
        val meta = requestMeta(type, id) ?: return SAnimeSeasonUpdate(anime, seasons)
        return SAnimeSeasonUpdate(
            anime = if (fetchDetails) StremioMapper.toSAnime(meta, type) else anime,
            seasons = if (fetchSeasons) StremioMapper.toSeasons(meta, type) else seasons,
        )
    }

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getAnimeSeasonUpdate"))
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> {
        val (type, id) = StremioMapper.parseEntryUrl(anime.url) ?: return emptyList()
        val meta = requestMeta(type, id) ?: return emptyList()
        return StremioMapper.toSeasons(meta, type)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val (type, id) = StremioMapper.parseEntryUrl(anime.url) ?: return anime
        val meta = requestMeta(type, id) ?: return anime
        return StremioMapper.toSAnime(meta, type)
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val season = StremioMapper.parseSeasonUrl(anime.url)
        val (type, id) = StremioMapper.parseEntryUrl(season?.first ?: anime.url) ?: return emptyList()
        val meta = requestMeta(type, id) ?: return emptyList()
        return StremioMapper.toEpisodes(meta, type, onlySeason = season?.second)
    }

    /**
     * Every installed addon that can answer for this episode, asked at once.
     *
     * This is the one place the app must stop treating an addon as a self-contained source. In
     * Stremio the catalogue and the streams are deliberately different addons: Cinemeta knows what
     * *Spider-Man* is and has no video at all, while a stream provider has video and no idea what
     * it is called. They meet on the id — that is the entire reason Stremio identifies things by
     * IMDb id rather than by its own numbering.
     *
     * So a title opened from a metadata addon asks every stream addon that will take its id, and
     * each one becomes a hoster named after itself, because "which addon found this" is exactly
     * the distinction the hoster list exists to draw. Asking them together rather than in turn
     * matters: stream providers are the slowest thing here, and in sequence a title with four
     * installed addons would take four round trips before the first video appeared.
     */
    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val (type, id) = StremioMapper.parseEntryUrl(episode.url) ?: return emptyList()
        val providers = streamProvidersFor(type, id)
        if (providers.isEmpty()) {
            // Not "no videos": this addon is a listing, and the fix is to install a second addon.
            // Saying so is the difference between a dead end and a next step.
            throw IllegalStateException(
                Injekt.get<Application>().stringResource(AYMR.strings.stremio_no_stream_addon),
            )
        }

        val hosters = coroutineScope {
            // Started before the streams rather than after them: subtitle providers are a separate
            // set of addons and a separate round trip, and asking in sequence would add their
            // latency to a wait that is already the slowest thing in the app.
            val subtitles = async { subtitlesFor(type, id) }
            val answered = providers
                .map { provider -> async { hosterFor(provider, type, id) } }
                .awaitAll()
                .filterNotNull()
            withSubtitles(answered, subtitles.await())
        }
        return withoutUnplayableTorrents(hosters)
    }

    /**
     * Every installed addon's subtitles for this episode, in one list.
     *
     * The same composition streams use, for the same reason: a subtitle addon knows nothing about
     * catalogues and a catalogue knows nothing about subtitles, and they meet on the id. It is
     * asked once per episode rather than once per stream — a provider can match a specific release
     * by file size and name, but there is one subtitle list on screen and several streams behind
     * it, so a per-stream answer would be thrown away the moment somebody switched quality.
     */
    private suspend fun subtitlesFor(type: String, id: String): List<Track> {
        val native = providersOfSubtitles(type, id)
        if (native.isNotEmpty()) return askForSubtitles(native, type, id)

        // Nobody speaks this id. Try the one it can be turned into — see [imdbIdFor].
        val translated = runCatching { imdbIdFor(type, id) }.getOrNull() ?: return emptyList()
        val byImdb = providersOfSubtitles(type, translated)
        if (byImdb.isEmpty()) return emptyList()
        return askForSubtitles(byImdb, type, translated)
    }

    private fun providersOfSubtitles(type: String, id: String) = addonStore.addons.value.filter {
        it.manifest.canServe(RESOURCE_SUBTITLES, type, id)
    }

    private suspend fun askForSubtitles(
        providers: List<StremioAddon>,
        type: String,
        id: String,
    ): List<Track> = coroutineScope {
        providers.map { provider ->
            async {
                runCatching {
                    val url = StremioUrls.subtitles(provider.url, type, id)
                    val response = client.newCall(GET(url, headers)).awaitSuccess()
                    with(json) { response.parseAs<StremioSubtitleResponse>() }.subtitles
                }.getOrElse {
                    logcat(LogPriority.INFO, it) { "Stremio subtitles: ${provider.url} did not answer" }
                    emptyList()
                }
            }
        }.awaitAll().flatten().let(StremioMapper::toTracks)
    }

    /**
     * This episode's id in the one dialect every subtitle addon speaks.
     *
     * Subtitle addons are IMDb-only in practice — OpenSubtitles declares `idPrefixes: ["tt"]` and
     * the rest follow it. Anime Kitsu identifies everything as `kitsu:41370:5`, so an anime opened
     * through it was filtered out before a single request went anywhere, and the subtitle work did
     * not reach the catalogue an anime app's users are most likely to install. Silently, because
     * "no subtitles offered" is what a working addon with nothing to offer also looks like.
     *
     * Two ways across, cheapest first:
     *
     * 1. **The addon says so.** Meta objects carry an optional `imdb_id`, and where it is present
     *    it is exact — no matching, no chance of the wrong show. The season and episode come from
     *    the same document's own entry for this video, which is better than either guessing or
     *    reading them out of a title.
     * 2. **Ask a catalogue.** Failing that, the title is searched against a catalogue addon that
     *    does speak IMDb, which is the same bridge ordinary extensions already cross and holds
     *    itself to the same rule: an exact title match or nothing.
     *
     * Only reached when no installed provider would take the native id, so the extra request is
     * paid for by episodes that would otherwise have shown an empty subtitle list.
     */
    private suspend fun imdbIdFor(type: String, id: String): String? {
        if (id.startsWith(IMDB_PREFIX)) return null
        val meta = requestMeta(type, id.substringBefore(':')) ?: return null

        val video = meta.videos.firstOrNull { it.id == id }
        val season = video?.season ?: 1
        val episode = video?.episode ?: 1

        val imdb = meta.imdbId?.takeIf { it.startsWith(IMDB_PREFIX) }
            ?: StremioSubtitleFinder().resolve(meta.name)
            ?: return null

        return if (type == TYPE_MOVIE) imdb else "$imdb:$season:$episode"
    }

    /**
     * Addon subtitles appended to whatever each stream already carried.
     *
     * Appended, never substituted: a stream that ships its own subtitles is offering the ones timed
     * for that exact release, and those deserve to stay first in the list.
     */
    private fun withSubtitles(hosters: List<Hoster>, subtitles: List<Track>): List<Hoster> {
        if (subtitles.isEmpty()) return hosters
        return hosters.map { hoster ->
            hoster.copy(
                videoList = hoster.videoList?.map { video ->
                    video.copy(subtitleTracks = video.subtitleTracks + subtitles)
                },
            )
        }
    }

    /**
     * Drop the torrents when nothing here can play a torrent.
     *
     * TorrServer ships switched off, and the player only routes a magnet through it when it is
     * on — otherwise the link goes straight to mpv, which cannot open a magnet and does not say
     * so. It shows a spinner instead, forever. From a device: *"it keeps loading, I do not know
     * whether it is going to work at all"*, which is exactly what a video that can never start
     * looks like.
     *
     * Most Stremio stream addons are torrent addons, so this is not a rare corner — and being
     * told to turn on a setting is a fix, while a spinner is not.
     */
    private fun withoutUnplayableTorrents(hosters: List<Hoster>): List<Hoster> {
        if (torrentPreferences.torrServerEnable().get()) return hosters

        val playable = hosters.mapNotNull { hoster ->
            val kept = hoster.videoList.orEmpty().filterNot { it.videoUrl.startsWith(MAGNET_SCHEME) }
            if (kept.isEmpty()) null else hoster.copy(videoList = kept)
        }
        if (playable.isEmpty() && hosters.isNotEmpty()) {
            throw IllegalStateException(
                Injekt.get<Application>().stringResource(AYMR.strings.stremio_torrents_disabled),
            )
        }
        return playable
    }

    /**
     * Streams arrive complete from the one `/stream` call, so a hoster is already carrying its
     * videos by the time the player asks for them and there is nothing left to fetch.
     */
    override suspend fun getVideoList(hoster: Hoster): List<Video> = hoster.videoList.orEmpty()

    /**
     * This addon first when it serves streams, then everything else that will take this id.
     *
     * Order is the answer to "which of these did the user mean": an addon that both lists and
     * streams is the one they opened, so its own videos lead.
     */
    private fun streamProvidersFor(type: String, id: String): List<StremioAddon> {
        val installed = addonStore.addons.value
        val self = addon.takeIf { it.manifest.canServe(RESOURCE_STREAM, type, id) }
        val others = installed.filter {
            !it.url.equals(addon.url, ignoreCase = true) && it.manifest.canServe(RESOURCE_STREAM, type, id)
        }
        return listOfNotNull(self) + others
    }

    /**
     * One addon's answer, or nothing.
     *
     * A provider that is down, slow or simply has this title is not an error for the others — the
     * whole point of asking several is that any one of them may come back empty.
     */
    private suspend fun hosterFor(provider: StremioAddon, type: String, id: String): Hoster? = runCatching {
        val response = client.newCall(GET(StremioUrls.stream(provider.url, type, id), headers)).awaitSuccess()
        val streams = with(json) { response.parseAs<StremioStreamResponse>() }.streams
        StremioMapper.toVideos(streams).takeIf { it.isNotEmpty() }?.let { videos ->
            Hoster(
                hosterUrl = provider.url,
                hosterName = provider.manifest.name.ifBlank { provider.url },
                videoList = videos,
            )
        }
    }.getOrElse {
        logcat(LogPriority.INFO, it) { "Stremio stream provider ${provider.url} did not answer for $type/$id" }
        null
    }

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
            if (query.isNotBlank()) put(EXTRA_SEARCH, query)
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

    /**
     * The catalogs behind one shelf — one per content type, not one in total.
     *
     * Catalogs are published per type, and a shelf is not. Cinemeta has eight: Popular, New and
     * Featured, each once for films and once for series. Picking the first match gave a Popular
     * shelf of films only, with every series in the addon reachable solely through the filter
     * sheet — in an app whose whole subject is episodic content. So a shelf takes the best match
     * *for each type* and interleaves them.
     *
     * Capped, because an addon with a dozen types would otherwise turn one shelf into a dozen
     * requests. Anything past the cap is still in the filter sheet, where it always was.
     */
    private fun catalogsFor(words: List<String>, fallbackIndex: Int): List<StremioCatalog> {
        val browsable = browsableCatalogs()
        val byType = browsable
            .filter { catalog -> words.any { catalog.displayName.contains(it, true) } }
            .groupBy { it.type }
            .values
            .mapNotNull { it.firstOrNull() }
            .take(MAX_CATALOGS_PER_SHELF)
        if (byType.isNotEmpty()) return byType
        // No catalog says what it is, so fall back to position: the first for Popular, the second
        // for Latest, which is the order addons tend to list them in anyway.
        return listOfNotNull(browsable.getOrNull(fallbackIndex))
    }

    private fun popularCatalogs(): List<StremioCatalog> = catalogsFor(POPULAR_WORDS, fallbackIndex = 0)

    private fun latestCatalogs(): List<StremioCatalog> = catalogsFor(LATEST_WORDS, fallbackIndex = 1)

    private fun popularCatalog(): StremioCatalog? = popularCatalogs().firstOrNull()

    /**
     * Several catalogs as one page, taken in turns.
     *
     * Concatenating would put a hundred films above the first series, which on a phone is the same
     * as not having the series. Taking one from each in turn puts both types on the first screen,
     * which is the point of asking for both.
     */
    private suspend fun fetchShelf(catalogs: List<StremioCatalog>, page: Int): AnimesPage {
        if (catalogs.isEmpty()) return AnimesPage(emptyList(), false)
        if (catalogs.size == 1) return fetchCatalog(catalogs.first(), page, query = "", genre = null)

        val pages = coroutineScope {
            catalogs
                .map { catalog -> async { runCatching { fetchCatalog(catalog, page, "", null) }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }
        val interleaved = buildList {
            val lists = pages.map { it.animes }
            for (index in 0 until (lists.maxOfOrNull { it.size } ?: 0)) {
                lists.forEach { list -> list.getOrNull(index)?.let(::add) }
            }
        }
        return AnimesPage(
            animes = interleaved.distinctBy { it.url },
            hasNextPage = pages.any { it.hasNextPage },
        )
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

        /** One shelf, at most this many catalogs — one per content type, up to a sane ceiling. */
        private const val MAX_CATALOGS_PER_SHELF = 3
        private const val EXTRA_SEARCH = "search"
        private const val EXTRA_GENRE = "genre"
        private const val EXTRA_SKIP = "skip"
        private const val RESOURCE_META = "meta"
        private const val RESOURCE_STREAM = "stream"
        private const val RESOURCE_SUBTITLES = "subtitles"
        private const val FILTER_CATALOG = "Catalog"
        private const val ANY_GENRE = "Any"
        private const val IMDB_PREFIX = "tt"

        /** A film's subtitle id is the bare IMDb id; a series' carries season and episode. */
        private const val TYPE_MOVIE = "movie"
        private const val IMDB_TITLE_URL = "https://www.imdb.com/title/"

        /** The same prefix the player and the downloader test for when routing to TorrServer. */
        private const val MAGNET_SCHEME = "magnet"

        private val POPULAR_WORDS = listOf("popular", "top", "trending", "featured")
        private val LATEST_WORDS = listOf("latest", "new", "recent", "airing", "updated")
    }
}
