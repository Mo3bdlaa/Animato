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
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

    init {
        combine(
            getUnifiedLibrary.subscribe(),
            contentPreferences.contentFilter.changes(),
            recent.changes(),
        ) { library, lens, recentQueries ->
            entries = library
            Triple(library, lens, recentQueries)
        }
            .onEach { (_, lens, recentQueries) ->
                state.value = state.value.copy(
                    lens = lens,
                    recentQueries = recentQueries.sorted(),
                )
                state.value = state.value.copy(libraryHits = libraryHits(state.value.query))
                if (state.value.query.isNotBlank()) search(state.value.query)
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
        state.value = state.value.copy(query = query)
        state.value = state.value.copy(libraryHits = libraryHits(query))
        if (query.isBlank()) {
            searchJob?.cancel()
            state.value = state.value.copy(sourceGroups = emptyList())
        }
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

        val sources = buildList {
            if (state.value.admits(ContentType.MANGA)) {
                sourceManager.getOnlineSources()
                    .filterIsInstance<CatalogueSource>()
                    .forEach { add(SearchTarget.Manga(it)) }
            }
            if (state.value.admits(ContentType.ANIME)) {
                animeSourceManager.getOnlineSources()
                    .filterIsInstance<AnimeCatalogueSource>()
                    .forEach { add(SearchTarget.Anime(it)) }
            }
        }

        state.value = state.value.copy(
            query = query,
            hasSearchableSources = sources.isNotEmpty(),
            sourceGroups = sources.map { it.emptyGroup() },
        )
        remember(query)

        searchJob = viewModelScope.launch {
            coroutineScope {
                sources.forEach { target ->
                    async { runSearch(target, query) }
                }
            }
        }
    }

    private suspend fun runSearch(target: SearchTarget, query: String) {
        val result = runCatching { target.search(query) }
        state.value = state.value.copy(
            sourceGroups = state.value.sourceGroups.map { group ->
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

    private fun libraryHits(query: String): List<LibraryHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return entries
            .asSequence()
            .filter { state.value.admits(it.contentType) }
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
        val contentType: ContentType

        suspend fun search(query: String): List<SourceHit>

        fun emptyGroup() = SourceGroup(sourceId = id, sourceName = name, contentType = contentType)

        class Manga(private val source: CatalogueSource) : SearchTarget {
            override val id = source.id
            override val name = source.name
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
    }
}
