package animato.app.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.app.discover.MetadataCatalog
import animato.app.discover.MetadataRail
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import animato.domain.content.interactor.GetUnifiedLibrary
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A title already on the shelf. Drawn as a row, because finding it again is retrieval. */
@Immutable
data class LibraryHit(
    val entryId: Long,
    val contentType: ContentType,
    val title: String,
    val coverData: Any?,
    val viewedItems: Long,
    val totalItems: Long,
    val unviewedItems: Long,
)

/** A title a source is offering. Drawn as a card, because deciding is appraisal. */
@Immutable
data class SourceHit(
    val key: String,
    val title: String,
    val coverData: Any?,
    val url: String,
    val sourceId: Long,
    val contentType: ContentType,
)

/**
 * One source's answer.
 *
 * A group exists as soon as the source is asked, so a slow source shows as *searching* in its own
 * place rather than as an absence. [failure] is the source's own name for what went wrong, kept
 * because a group that silently returns nothing and a group that got a 403 look identical
 * otherwise.
 */
@Immutable
data class SourceGroup(
    val sourceId: Long,
    val sourceName: String,
    /**
     * Shown beside the name, because a name is not an identity here.
     *
     * A site that publishes in ten languages is ten sources with one name, and a screen listing all
     * of them without this reads as the same source repeated ten times.
     */
    val lang: String,
    val contentType: ContentType,
    val isSearching: Boolean = true,
    val hits: List<SourceHit> = emptyList(),
    val failure: String? = null,
)

@Immutable
data class SearchState(
    val query: String = "",
    val lens: ContentFilter = ContentFilter.ALL,
    val libraryHits: List<LibraryHit> = emptyList(),
    val sourceGroups: List<SourceGroup> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val trendingQueries: List<String> = emptyList(),
    val hasSearchableSources: Boolean = true,
    /** Set when the caller already knew the medium. See `AnimatoSearchScreen.restrictTo`. */
    val restrictTo: ContentType? = null,
) {
    val isIdle: Boolean get() = query.isBlank()

    /** The lens as this search sees it: the standing preference, unless the caller narrowed it. */
    val effectiveLens: ContentFilter
        get() = when (restrictTo) {
            ContentType.ANIME -> ContentFilter.ANIME
            ContentType.MANGA -> ContentFilter.MANGA
            null -> lens
        }

    fun admits(type: ContentType): Boolean = effectiveLens.accepts(type)

    /*
     * The four states a source can be in, kept apart so the screen can put them in the order
     * somebody reads them in.
     *
     * Listing every source in one flat list is what a device saw: twenty rows, most of them a name
     * and a nought, with the three sources that actually found something buried among them. The
     * ones with answers come first now; the rest collapse.
     */
    val answered: List<SourceGroup>
        get() = sourceGroups.filter { !it.isSearching && it.hits.isNotEmpty() }

    val searching: List<SourceGroup> get() = sourceGroups.filter { it.isSearching }

    val empty: List<SourceGroup>
        get() = sourceGroups.filter { !it.isSearching && it.failure == null && it.hits.isEmpty() }

    val failed: List<SourceGroup> get() = sourceGroups.filter { !it.isSearching && it.failure != null }

    val totalHits: Int get() = sourceGroups.sumOf { it.hits.size }
}

/**
 * One field, the library first, then every source separately.
 *
 * ## Library first, always
 *
 * Even when the shelf has one hit and the sources have twelve. The most common search anyone
 * performs is *where is that thing I already have*, and putting the shelf at the top answers it
 * without anybody having to reach for a filter first.
 *
 * The two kinds of result get two different shapes on purpose. A library hit is a row — title,
 * where you are in it, one tap to resume — because it is retrieval. A source hit is a card — cover,
 * source, is this the one — because it is appraisal. Same-shaped results would have needed a
 * heading to tell them apart, and a heading is a worse answer than a shape.
 *
 * ## One slow source never holds the others hostage
 *
 * Each source is asked in its own coroutine and its group updates when it answers. That is the
 * whole reason a group appears in the *searching* state rather than being added on completion: a
 * spinner over the screen would mean the fastest source's eight results wait for the slowest one's
 * timeout.
 *
 * ## Where the asking happens, which froze the app
 *
 * On [SEARCH_DISPATCHER], and never on the main thread. `viewModelScope` is the main dispatcher, so
 * launching the searches on it ran every source's network call on the UI thread — and an extension
 * is free to implement its search with a blocking request, so the whole app stopped until the last
 * of them returned. From a device: *"clicking on any of trending makes the app get stuck."*
 *
 * The cap is the other half. A search asks every installed source of both halves at once, and
 * twenty simultaneous requests is how a phone gets itself rate limited by the same host three times
 * over. Five at a time is what Mihon's own global search settled on.
 *
 * ## What the lens does here
 *
 * Under a narrowed lens the other medium's source groups do not appear at all. That is the visible
 * answer to *"I can't search to add an anime"* — which was true, because the only search reachable
 * from the old screens was per-half and the half depended on where you had come from.
 */
class SearchScreenModel(
    getUnifiedLibrary: GetUnifiedLibrary = Injekt.get(),
    contentPreferences: ContentPreferences = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val animeSourcePreferences: AnimeSourcePreferences = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val metadataCatalog: MetadataCatalog = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<SearchState>
        field = MutableStateFlow(SearchState(lens = contentPreferences.contentFilter.get()))

    private val recent = preferenceStore.getStringSet(RECENT_KEY, emptySet())

    /** The whole library, held so that typing filters an in-memory list rather than re-querying. */
    private var entries: List<LibraryEntry> = emptyList()

    private var searchJob: Job? = null

    /*
     * Three collectors, not one combine, because only one of the three is a reason to search again.
     *
     * They used to be combined, and the search re-ran on every emission of any of them. Two of the
     * three fire *because* a search ran:
     *
     *   - `search` records the query in the history, so `recent` emits, so the search restarts and
     *     cancels the one that had just begun
     *   - opening a source result inserts the entry, so the library emits, so the search restarts
     *
     * The visible symptom was source rows reading `StandaloneCoroutine was cancelled` where their
     * results should have been: those are the sources that were still answering the first search
     * when the second one killed it.
     *
     * The lens is the only one that genuinely changes the answer, because it changes which sources
     * are asked.
     */
    init {
        getUnifiedLibrary.subscribe()
            .onEach { library ->
                entries = library
                state.update { it.copy(libraryHits = libraryHits(it.query, it)) }
            }
            .launchIn(viewModelScope)

        recent.changes()
            .onEach { queries -> state.update { it.copy(recentQueries = queries.sorted()) } }
            .launchIn(viewModelScope)

        contentPreferences.contentFilter.changes()
            .onEach { lens ->
                state.update { it.copy(lens = lens) }
                state.update { it.copy(libraryHits = libraryHits(it.query, it)) }
                state.value.query.takeIf { it.isNotBlank() }?.let(::search)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch { loadTrending() }
    }

    /**
     * Trending titles, offered as queries rather than as results.
     *
     * An empty field is a screen with nothing on it, and the never-empty rule applies here as much
     * as anywhere. These are the same titles Discover's first rail shows, which is deliberate: a
     * suggestion you have already seen elsewhere reads as a suggestion, not as a result.
     */
    private suspend fun loadTrending() {
        val lens = state.value.lens
        val type = if (lens == ContentFilter.ANIME) ContentType.ANIME else ContentType.MANGA
        val titles = metadataCatalog.fetch(MetadataRail.TRENDING, type).take(TRENDING_LIMIT).map { it.title }
        state.value = state.value.copy(trendingQueries = titles)
    }

    /**
     * Narrows this screen to one medium for as long as it is open.
     *
     * Separate from [search] because it has to be in place before the first query runs, and because
     * clearing it — a screen opened with no restriction, reusing the model of one that had one — is
     * as much a part of it as setting it.
     */
    fun restrictTo(contentType: ContentType?) {
        state.value = state.value.copy(restrictTo = contentType)
    }

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
        state.update { it.copy(libraryHits = libraryHits(query, it)) }
        if (query.isBlank()) {
            searchJob?.cancel()
            state.update { it.copy(sourceGroups = emptyList()) }
        }
    }

    private var seeded = false

    /**
     * Runs the query a screen was *opened with*, once per model — a seed, not a standing
     * instruction.
     *
     * The screen calls this from a `LaunchedEffect`, and Voyager recomposes a screen from scratch
     * every time it comes back to the top, so the effect fires again on every return. From a
     * device: search from a trending title, open a result, come back — *"it searches again instead
     * of continuing or just showing the results."* This model outlives that round trip, so the
     * results are all still here; re-running the seed threw them away and, worse, would clobber
     * any different query typed in the meantime with the one the screen was opened with.
     */
    fun seedSearch(query: String) {
        if (seeded) return
        seeded = true
        onQueryChange(query)
        search(query)
    }

    /**
     * Ask every source the lens admits.
     *
     * Cancelling the previous job first is what makes a second search cheap: the sources from the
     * first one are still answering, and their results belong to a query nobody is looking at.
     */
    fun search(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()

        val targets = searchTargets()

        state.update {
            it.copy(
                query = query,
                hasSearchableSources = targets.isNotEmpty(),
                sourceGroups = targets.map { target -> target.emptyGroup() },
            )
        }
        remember(query)

        searchJob = viewModelScope.launch(SEARCH_DISPATCHER) {
            coroutineScope {
                targets.forEach { target ->
                    async { runSearch(target, query) }
                }
            }
        }
    }

    /**
     * The sources this search asks, in the order they appear.
     *
     * ## What the screen looked like without the filters
     *
     * Eight rows saying *Dragon Ball Multiverse*, four saying *The Library of Ohara*, and the
     * results buried underneath. Those are not duplicates: an extension for a site that publishes in
     * ten languages registers ten sources, one per language, all with the same name. Nothing on the
     * row said which was which, and every one of them was asked.
     *
     * So the same two settings the rest of the app respects apply here — the enabled languages,
     * which is the control on the Sources screen, and the disabled sources, because hiding a source
     * and then being asked to read its results is the setting not working. The language is on the
     * row as well, since two sources may still share a name legitimately.
     *
     * Pinned sources come first. Whoever pinned one wants its answer before the other nineteen, and
     * this is the only place in the app where twenty answers arrive in an order somebody has to sit
     * and watch.
     */
    private fun searchTargets(): List<SearchTarget> {
        val languages = sourcePreferences.enabledLanguages.get()
        val disabledManga = sourcePreferences.disabledSources.get()
        val disabledAnime = animeSourcePreferences.disabledAnimeSources.get()
        val pinned = sourcePreferences.pinnedSources.get() +
            animeSourcePreferences.pinnedAnimeSources.get()

        return buildList {
            if (state.value.admits(ContentType.MANGA)) {
                sourceManager.getOnlineSources()
                    .filterIsInstance<CatalogueSource>()
                    .filter { it.lang in languages && it.id.toString() !in disabledManga }
                    .forEach { add(SearchTarget.Manga(it)) }
            }
            if (state.value.admits(ContentType.ANIME)) {
                animeSourceManager.getOnlineSources()
                    .filterIsInstance<AnimeCatalogueSource>()
                    .filter { it.lang in languages && it.id.toString() !in disabledAnime }
                    .forEach { add(SearchTarget.Anime(it)) }
            }
        }
            .distinctBy { it.contentType to it.id }
            .sortedWith(
                compareBy<SearchTarget> { it.id.toString() !in pinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.lang },
            )
    }

    private suspend fun runSearch(target: SearchTarget, query: String) {
        // Not `runCatching`, which catches CancellationException as though it were a failure. A
        // cancelled search would then report itself on the row as "StandaloneCoroutine was
        // cancelled" — which is what a device saw — and, worse, would go on writing state after the
        // job that owned it was gone.
        val result = try {
            Result.success(target.search(query))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        // Cancelling a job does not unwind a source that is already answering, so a search left
        // behind by a faster second one can still arrive here. Without this it writes its hits into
        // the group belonging to the query on screen — the right source, the wrong question.
        if (state.value.query != query) return

        // `update` rather than an assignment: these run concurrently now, and read-modify-write on
        // a shared value loses whichever two sources answer closest together.
        state.update { current ->
            current.copy(
                sourceGroups = current.sourceGroups.map { group ->
                    if (group.sourceId != target.id || group.contentType != target.contentType) {
                        group
                    } else {
                        group.copy(
                            isSearching = false,
                            hits = result.getOrDefault(emptyList()),
                            failure = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName },
                        )
                    }
                },
            )
        }
    }

    // Takes the state it filters against rather than reading `state.value`, so it can be called
    // from inside an `update` block without reading a value that block is about to replace.
    private fun libraryHits(query: String, against: SearchState): List<LibraryHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return entries
            .asSequence()
            .filter { against.admits(it.contentType) }
            .filter { it.title.contains(trimmed, ignoreCase = true) }
            .distinctBy { it.contentType to it.entryId }
            .take(LIBRARY_LIMIT)
            .map {
                LibraryHit(
                    entryId = it.entryId,
                    contentType = it.contentType,
                    title = it.title,
                    coverData = it.coverData,
                    viewedItems = it.viewedItems,
                    totalItems = it.totalItems,
                    unviewedItems = it.unviewedItems,
                )
            }
            .toList()
    }

    /**
     * Remembers a query, capped.
     *
     * A set rather than a list because that is what the preference store offers — which also means
     * the history has no order of its own and is shown alphabetically rather than pretending to be
     * chronological. The cap is applied on write: a history nobody has pruned is one nobody reads.
     */
    private fun remember(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        recent.set((recent.get() + trimmed).toList().takeLast(RECENT_LIMIT).toSet())
    }

    fun clearRecent() = recent.set(emptySet())

    /** Turns a source result into an entry the rest of the app can open. */
    suspend fun resolveEntryId(hit: SourceHit): Long = when (hit.contentType) {
        ContentType.MANGA -> networkToLocalManga(
            tachiyomi.domain.manga.model.Manga.create().copy(
                url = hit.url,
                title = hit.title,
                source = hit.sourceId,
            ),
        ).id
        ContentType.ANIME -> networkToLocalAnime.await(
            tachiyomi.domain.entries.anime.model.Anime.create().copy(
                url = hit.url,
                title = hit.title,
                source = hit.sourceId,
            ),
        ).id
    }

    /** A source of either half, asked the same question. */
    private sealed interface SearchTarget {

        val id: Long
        val name: String
        val lang: String
        val contentType: ContentType

        suspend fun search(query: String): List<SourceHit>

        fun emptyGroup() = SourceGroup(
            sourceId = id,
            sourceName = name,
            lang = lang,
            contentType = contentType,
        )

        class Manga(private val source: CatalogueSource) : SearchTarget {
            override val id = source.id
            override val name = source.name
            override val lang = source.lang
            override val contentType = ContentType.MANGA

            override suspend fun search(query: String): List<SourceHit> =
                source.getSearchManga(1, query, eu.kanade.tachiyomi.source.model.FilterList())
                    .mangas
                    .map { entry ->
                        val domain = entry.toDomainManga(source.id)
                        SourceHit(
                            key = "manga-${source.id}-${domain.url}",
                            title = domain.title,
                            coverData = domain.asMangaCover(),
                            url = domain.url,
                            sourceId = source.id,
                            contentType = ContentType.MANGA,
                        )
                    }
        }

        class Anime(private val source: AnimeCatalogueSource) : SearchTarget {
            override val id = source.id
            override val name = source.name
            override val lang = source.lang
            override val contentType = ContentType.ANIME

            override suspend fun search(query: String): List<SourceHit> =
                source.getSearchAnime(1, query, eu.kanade.tachiyomi.animesource.model.AnimeFilterList())
                    .animes
                    .map { entry ->
                        val domain = entry.toDomainAnime(source.id)
                        SourceHit(
                            key = "anime-${source.id}-${domain.url}",
                            title = domain.title,
                            coverData = domain.asAnimeCover(),
                            url = domain.url,
                            sourceId = source.id,
                            contentType = ContentType.ANIME,
                        )
                    }
        }
    }

    private companion object {
        const val RECENT_KEY = "animato_recent_searches"
        const val RECENT_LIMIT = 8
        const val LIBRARY_LIMIT = 10
        const val TRENDING_LIMIT = 6

        /** Five sources at a time, off the main thread. See the class comment. */
        val SEARCH_DISPATCHER = Dispatchers.IO.limitedParallelism(5)
    }
}
