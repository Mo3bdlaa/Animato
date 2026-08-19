package animato.app.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** One cover in a Discover rail. */
@Immutable
data class DiscoverItem(
    val key: String,
    val title: String,
    val coverData: Any,
    val sourceId: Long,
    val url: String,
    val contentType: ContentType,
)

@Immutable
data class DiscoverRail(
    val isLoading: Boolean = true,
    val items: List<DiscoverItem> = emptyList(),
    /** Sources that were asked and did not answer. Shown, not swallowed. */
    val failedSources: List<String> = emptyList(),
)

/** One metadata rail — one question, about one medium — and what it currently holds. */
@Immutable
data class MetadataRailState(
    val rail: MetadataRail,
    val contentType: ContentType,
    val isLoading: Boolean = true,
    val items: List<MetadataItem> = emptyList(),
) {
    val key: String get() = "${rail.name}-${contentType.name}"
}

@Immutable
data class DiscoverState(
    val lens: ContentFilter = ContentFilter.ALL,
    /** Whether any source can be asked at all. See `railSources`, which is not about pinning. */
    val hasSources: Boolean = true,
    val metadataRails: List<MetadataRailState> = emptyList(),
    val popular: DiscoverRail = DiscoverRail(),
    val latest: DiscoverRail = DiscoverRail(),
)

/**
 * The rails this lens asks for: every question, once per medium it applies to.
 *
 * ## Why not one rail holding both
 *
 * It used to ask both halves for each question and interleave the answers, which sounds like the
 * unified screen the app is about and was not. Trending anime and trending manga are two different
 * lists in the world, and shuffling them together produced one rail where the manga were whatever
 * happened to land between the anime — from a device: *"trending brings anime and manga, and this
 * season is anime only, so we want something for manga on its own."*
 *
 * Separating them is also what lets the covers drop their type marks. A rail is one medium now, its
 * header says which, and nothing on the cards has to repeat it.
 *
 * ## Why only trending now
 *
 * The screen carried all three questions per medium — five rails before your own sources ever
 * appeared — and a device called it: *"discover has a lot; trending anime and trending manga may
 * be enough."* The other questions did not disappear, they moved behind *view all*: the grid
 * screen shows any rail at full depth, so top-rated is one tap away instead of two screens of
 * scrolling closer.
 */
private fun railsFor(lens: ContentFilter): List<MetadataRailState> =
    listOf(ContentType.ANIME, ContentType.MANGA)
        .filter { lens.accepts(it) }
        .flatMap {
            listOf(
                MetadataRailState(rail = MetadataRail.TRENDING, contentType = it),
                MetadataRailState(rail = MetadataRail.SUGGESTED, contentType = it),
            )
        }

/**
 * What to watch or read next.
 *
 * ## Two kinds of rail, and why the top ones do not need a source
 *
 * The screen used to be nothing but the pinned sources’ own popular and latest pages, which meant a
 * fresh install — Animato ships with no sources — opened on an empty screen and a sentence
 * explaining the emptiness. Discover was the screen that most needed to work before you had
 * committed to anything, and it was the one that worked least.
 *
 * So the top of the screen is [MetadataCatalog]: trending, this season and top rated, from public
 * metadata, on a phone that has never installed an extension. Under them, *Your sources* is the old
 * behaviour — each pinned source’s popular and latest, interleaved so no single source fills a
 * rail — and that block is the only part that goes empty.
 *
 * ## Both halves at once
 *
 * The model reads the lens rather than being constructed for one content type. Under `ALL` the
 * public rails come in pairs — trending anime and trending manga, each its own shelf — while *Your
 * sources* interleaves, because there a rail is a set of sources rather than a medium. See
 * [railsFor] for why the metadata rails stopped interleaving.
 *
 * Every request is contained. A source that is down, rate limited or broken contributes nothing and
 * is named; a metadata rail that fails comes back empty. This is the one screen that reaches the
 * network before the user has asked for anything, so nothing on it may throw.
 */
class DiscoverScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val animeSourcePreferences: AnimeSourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val metadataCatalog: MetadataCatalog = Injekt.get(),
    private val getLibraryManga: GetLibraryManga = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val discoverCache: DiscoverCache = DiscoverCache(),
    contentPreferences: ContentPreferences = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<DiscoverState>
        field = MutableStateFlow<DiscoverState>(
            contentPreferences.contentFilter.get().let {
                DiscoverState(lens = it, metadataRails = railsFor(it))
            },
        )

    init {
        viewModelScope.launch {
            contentPreferences.contentFilter.changes().collectLatest { lens ->
                val rails = cachedRails(lens)
                state.value = cachedState(lens, rails)
                loadMetadata(rails)
                loadSourceRails(lens)
            }
        }
    }

    /**
     * Pull-to-refresh: the same rebuild a lens change does, for the lens already in place. The
     * cached shelves stay on screen while every rail asks again — a refresh that blanks the page
     * looks like a failure until it isn't.
     */
    fun refresh() {
        viewModelScope.launch {
            val lens = state.value.lens
            val rails = cachedRails(lens)
            state.value = cachedState(lens, rails)
            loadMetadata(rails)
            loadSourceRails(lens)
        }
    }

    /**
     * The library, as seeds and as an exclusion list.
     *
     * Seeds are taken from the most recently added rather than at random: what somebody added last
     * is the best available guess at what they are in the mood for, and a rail that changes as the
     * library grows is doing its job. The whole library — not just the seeds — is what gets
     * excluded, since suggesting something already on the shelf is the one way this can be useless.
     */
    private suspend fun suggestionsFor(contentType: ContentType): List<MetadataItem> {
        val titles = when (contentType) {
            ContentType.MANGA -> getLibraryManga.await().map { it.manga.title }
            ContentType.ANIME -> getLibraryAnime.await().map { it.anime.title }
        }
        if (titles.isEmpty()) return emptyList()
        return metadataCatalog.suggestions(
            seedTitles = titles.asReversed(),
            contentType = contentType,
            exclude = titles.map(::normaliseTitle).toSet(),
        )
    }

    /** The rails for this lens, pre-filled from [DiscoverCache] so the screen never opens empty. */
    private fun cachedRails(lens: ContentFilter): List<MetadataRailState> =
        railsFor(lens).map { slot ->
            val cached = discoverCache.loadMetadata(slot.key, slot.contentType)
            if (cached.isEmpty()) slot else slot.copy(isLoading = false, items = cached)
        }

    private fun cachedState(lens: ContentFilter, rails: List<MetadataRailState>): DiscoverState {
        val popular = discoverCache.loadSourceRail("popular", lens)
        val latest = discoverCache.loadSourceRail("latest", lens)
        return DiscoverState(
            lens = lens,
            metadataRails = rails,
            popular = if (popular.isEmpty()) DiscoverRail() else DiscoverRail(isLoading = false, items = popular),
            latest = if (latest.isEmpty()) DiscoverRail() else DiscoverRail(isLoading = false, items = latest),
        )
    }

    /**
     * The public rails, each loading on its own.
     *
     * Separate launches rather than one sequential pass: the rails are independent, and a screen
     * that waits for the slowest before drawing any of them looks broken on a slow connection when
     * in fact all but one arrived.
     */
    private fun CoroutineScope.loadMetadata(rails: List<MetadataRailState>) {
        rails.forEach { slot ->
            launch {
                val items = when (slot.rail) {
                    MetadataRail.SUGGESTED -> suggestionsFor(slot.contentType)
                    else -> metadataCatalog.fetch(slot.rail, slot.contentType)
                }
                if (items.isNotEmpty()) {
                    discoverCache.saveMetadata(slot.key, items)
                }
                // `update` rather than an assignment: five of these are in flight at once, and
                // read-modify-write on a shared value loses whichever two land closest together.
                state.update { current ->
                    current.copy(
                        metadataRails = current.metadataRails.map { existing ->
                            when {
                                existing.key != slot.key -> existing
                                // A failed fetch does not blank a shelf the cache filled.
                                items.isEmpty() -> existing.copy(isLoading = false)
                                else -> existing.copy(isLoading = false, items = items)
                            }
                        },
                    )
                }
            }
        }
    }

    /** A fresh answer replaces the shelf; a failed one leaves the cached shelf standing. */
    private fun DiscoverRail.keepCacheUnless(fresh: DiscoverRail): DiscoverRail =
        if (fresh.items.isEmpty() && items.isNotEmpty()) {
            copy(isLoading = false, failedSources = fresh.failedSources)
        } else {
            fresh
        }

    private fun CoroutineScope.loadSourceRails(lens: ContentFilter) {
        launch {
            railSources(lens).collectLatest { sourcesByType ->
                val anySources = sourcesByType.values.any { it.isNotEmpty() }
                state.update { it.copy(hasSources = anySources) }
                if (!anySources) return@collectLatest

                // On [SOURCE_DISPATCHER], and never on the main thread. `viewModelScope` is the
                // main dispatcher, and an extension is free to implement its popular page with a
                // blocking request — so this used to stop the whole app until every source had
                // answered or timed out.
                launch(SOURCE_DISPATCHER) {
                    val rail = load(sourcesByType, latest = false)
                    if (rail.items.isNotEmpty()) discoverCache.saveSourceRail("popular", lens, rail.items)
                    state.update { it.copy(popular = it.popular.keepCacheUnless(rail)) }
                }
                launch(SOURCE_DISPATCHER) {
                    val rail = load(sourcesByType, latest = true)
                    if (rail.items.isNotEmpty()) discoverCache.saveSourceRail("latest", lens, rail.items)
                    state.update { it.copy(latest = it.latest.keepCacheUnless(rail)) }
                }
            }
        }
    }

    /**
     * Which sources *Your sources* asks, per half the lens admits.
     *
     * ## It used to mean "pinned", and pinning is opt-in
     *
     * From a device: *"I have sources but the sources section shows no sources at all."* Exactly
     * right — the rails read the pinned set, which is empty until somebody goes and pins something,
     * and nothing on the screen said that was the question being asked. So a phone with a dozen
     * working extensions got the same *you have no sources* card as a fresh install, under a heading
     * that claims to be about the sources you have, next to a button offering to install more.
     *
     * Pinning still wins when it exists: it is the one explicit statement of which sources somebody
     * actually wants asked. It is a preference now rather than a precondition.
     *
     * ## Why the unpinned case is capped
     *
     * Both rails walk the whole list, so an unpinned phone with thirty extensions would open this
     * screen by starting sixty requests it did not ask for. [UNPINNED_SOURCES] is what it takes
     * instead — a rail shows [RAIL_LIMIT] items regardless, so asking more sources mostly buys
     * failure modes. Pinning is how somebody says which ones; this is only the answer before they
     * have.
     *
     * Disabled sources are dropped in both cases: hiding a source and then being shown its popular
     * page is the setting not working.
     */
    private fun railSources(lens: ContentFilter): Flow<Map<ContentType, List<Long>>> = combine(
        if (lens.includesManga) mangaRailSources() else flowOf(emptyList()),
        if (lens.includesAnime) animeRailSources() else flowOf(emptyList()),
    ) { manga, anime ->
        buildMap {
            if (lens.includesManga) put(ContentType.MANGA, manga)
            if (lens.includesAnime) put(ContentType.ANIME, anime)
        }
    }

    // Built on the manager's own flow rather than a snapshot, so installing an extension makes its
    // source appear here without leaving the screen and coming back. `HttpSource` is what excludes
    // the local library, whose "popular" page is a folder on the phone.
    private fun mangaRailSources(): Flow<List<Long>> = combine(
        sourceManager.sources,
        sourcePreferences.pinnedSources.changes(),
        sourcePreferences.disabledSources.changes(),
    ) { sources, pinned, disabled ->
        choose(sources.filterIsInstance<HttpSource>().map { it.id }, pinned, disabled, nsfwMangaSources())
    }

    private fun animeRailSources(): Flow<List<Long>> = combine(
        animeSourceManager.sources,
        animeSourcePreferences.pinnedAnimeSources.changes(),
        animeSourcePreferences.disabledAnimeSources.changes(),
    ) { sources, pinned, disabled ->
        choose(sources.filterIsInstance<AnimeHttpSource>().map { it.id }, pinned, disabled, nsfwAnimeSources())
    }

    /**
     * Which sources this rail may ask, after the two standing exclusions.
     *
     * Disabled sources are excluded because hiding a source and then being shown its popular page
     * is the setting not working. NSFW sources are excluded outright — asked for from a device:
     * *"don't show NSFW in Discover under Your sources."* Discover is the front page, seen over
     * shoulders and without asking; somebody who wants an adult source's front page can open the
     * source itself, where the choice is theirs and deliberate. The preference that shows NSFW at
     * all is a separate question from putting it on the app's first screen.
     */
    private fun choose(
        available: List<Long>,
        pinned: Set<String>,
        disabled: Set<String>,
        nsfw: Set<Long>,
    ): List<Long> {
        val allowed = available.filter { it.toString() !in disabled && it !in nsfw }
        val pinnedIds = allowed.filter { it.toString() in pinned }
        return pinnedIds.ifEmpty { allowed.take(UNPINNED_SOURCES) }
    }

    /** Source ids belonging to an installed extension the repository marks NSFW. */
    private fun nsfwMangaSources(): Set<Long> =
        extensionManager.installedExtensionsFlow.value
            .filter { it.isNsfw }
            .flatMap { extension -> extension.sources.map { it.id } }
            .toSet()

    private fun nsfwAnimeSources(): Set<Long> =
        animeExtensionManager.installedExtensionsFlow.value
            .filter { it.isNsfw }
            .flatMap { extension -> extension.sources.map { it.id } }
            .toSet()

    private suspend fun load(
        sourcesByType: Map<ContentType, List<Long>>,
        latest: Boolean,
    ): DiscoverRail = coroutineScope {
        val results = sourcesByType
            .flatMap { (type, ids) -> ids.map { type to it } }
            .map { (type, sourceId) -> async { fetch(type, sourceId, latest) } }
            .awaitAll()

        DiscoverRail(
            isLoading = false,
            items = results.mapNotNull { it.getOrNull() }.interleave().take(RAIL_LIMIT),
            failedSources = results.mapNotNull { it.exceptionOrNull()?.sourceName },
        )
    }

    private suspend fun fetch(
        contentType: ContentType,
        sourceId: Long,
        latest: Boolean,
    ): Result<List<DiscoverItem>> {
        val sourceName = sourceNameOf(contentType, sourceId)
        return runCatching {
            when (contentType) {
                ContentType.MANGA -> {
                    val source = sourceManager.get(sourceId) as? CatalogueSource
                        ?: return@runCatching emptyList()
                    if (latest && !source.supportsLatest) return@runCatching emptyList()
                    val page = if (latest) source.getLatestUpdates(1) else source.getPopularManga(1)
                    page.mangas.map { manga ->
                        val domain = manga.toDomainManga(sourceId)
                        DiscoverItem(
                            key = "manga-$sourceId-${domain.url}",
                            title = domain.title,
                            coverData = domain.asMangaCover(),
                            sourceId = sourceId,
                            url = domain.url,
                            contentType = ContentType.MANGA,
                        )
                    }
                }
                ContentType.ANIME -> {
                    val source = animeSourceManager.get(sourceId) as? AnimeCatalogueSource
                        ?: return@runCatching emptyList()
                    if (latest && !source.supportsLatest) return@runCatching emptyList()
                    val page = if (latest) source.getLatestUpdates(1) else source.getPopularAnime(1)
                    page.animes.map { anime ->
                        val domain = anime.toDomainAnime(sourceId)
                        DiscoverItem(
                            key = "anime-$sourceId-${domain.url}",
                            title = domain.title,
                            coverData = domain.asAnimeCover(),
                            sourceId = sourceId,
                            url = domain.url,
                            contentType = ContentType.ANIME,
                        )
                    }
                }
            }
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Discover: $sourceName did not answer" }
        }.recoverCatching { throw SourceFailure(sourceName, it) }
    }

    private fun sourceNameOf(contentType: ContentType, sourceId: Long): String = when (contentType) {
        ContentType.MANGA -> sourceManager.getOrStub(sourceId).name
        ContentType.ANIME -> animeSourceManager.getOrStub(sourceId).name
    }

    /**
     * Resolves a rail item to the entry the rest of the app can open.
     *
     * A source result is not an entry yet — it has no id until it exists in the database — so this
     * is the same insert-or-fetch every browse screen does before navigating.
     */
    suspend fun resolveEntryId(item: DiscoverItem): Long = when (item.contentType) {
        ContentType.MANGA -> {
            val source = sourceManager.getOrStub(item.sourceId)
            networkToLocalManga(
                tachiyomi.domain.manga.model.Manga.create().copy(
                    url = item.url,
                    title = item.title,
                    source = source.id,
                ),
            ).id
        }
        ContentType.ANIME -> {
            networkToLocalAnime.await(
                tachiyomi.domain.entries.anime.model.Anime.create().copy(
                    url = item.url,
                    title = item.title,
                    source = item.sourceId,
                ),
            ).id
        }
    }

    companion object {
        /** A rail is scrolled, not paged; past this many nobody is still looking. */
        private const val RAIL_LIMIT = 30

        /** How many sources to ask when nobody has pinned any. See `railSources`. */
        private const val UNPINNED_SOURCES = 5

        /**
         * Off the main thread, and a few sources at a time.
         *
         * Both rails fan out to every pinned source at once and this screen loads without anybody
         * asking it to, so it is the last place that should be opening twenty connections on
         * arrival — a pinned list is short, but *popular* and *latest* each walk all of it.
         */
        private val SOURCE_DISPATCHER = Dispatchers.IO.limitedParallelism(5)
    }
}

/** Names the source behind a failure, so the screen can say which one is not answering. */
private class SourceFailure(val sourceName: String, cause: Throwable) : Exception(cause)

private val Throwable.sourceName: String?
    get() = (this as? SourceFailure)?.sourceName

/**
 * One from each list, then the next from each, and so on.
 *
 * Concatenating instead would let the first pinned source fill the whole rail, which is the
 * source-led browsing this screen exists to replace.
 */
private fun List<List<DiscoverItem>>.interleave(): List<DiscoverItem> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<DiscoverItem>(sumOf { it.size })
    var index = 0
    while (true) {
        var added = false
        forEach { items ->
            items.getOrNull(index)?.let {
                result += it
                added = true
            }
        }
        if (!added) return result
        index++
    }
}
