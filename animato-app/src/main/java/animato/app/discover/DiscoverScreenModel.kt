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
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.asAnimeCover
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
    val hasPinnedSources: Boolean = true,
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
 * Anime leads each pair because *This season* is anime-only, so a mixed screen that put manga first
 * would alternate media in an order no rule explains.
 */
private fun railsFor(lens: ContentFilter): List<MetadataRailState> =
    MetadataRail.entries.flatMap { rail ->
        listOf(ContentType.ANIME, ContentType.MANGA)
            .filter { it in rail.media && lens.accepts(it) }
            .map { MetadataRailState(rail = rail, contentType = it) }
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
                val rails = railsFor(lens)
                state.value = DiscoverState(lens = lens, metadataRails = rails)
                loadMetadata(rails)
                loadSourceRails(lens)
            }
        }
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
                val items = metadataCatalog.fetch(slot.rail, slot.contentType)
                state.value = state.value.copy(
                    metadataRails = state.value.metadataRails.map { existing ->
                        if (existing.key == slot.key) {
                            existing.copy(isLoading = false, items = items)
                        } else {
                            existing
                        }
                    },
                )
            }
        }
    }

    private fun CoroutineScope.loadSourceRails(lens: ContentFilter) {
        launch {
            pinnedSourceIds(lens).collectLatest { pinnedByType ->
                val anyPinned = pinnedByType.values.any { it.isNotEmpty() }
                state.value = state.value.copy(hasPinnedSources = anyPinned)
                if (!anyPinned) return@collectLatest

                launch {
                    state.value = state.value.copy(popular = load(pinnedByType, latest = false))
                }
                launch {
                    state.value = state.value.copy(latest = load(pinnedByType, latest = true))
                }
            }
        }
    }

    /** The pinned sources of each half the lens admits, as one flow that emits when either does. */
    private fun pinnedSourceIds(lens: ContentFilter): Flow<Map<ContentType, Set<String>>> = combine(
        if (lens.includesManga) sourcePreferences.pinnedSources.changes() else flowOf(emptySet()),
        if (lens.includesAnime) animeSourcePreferences.pinnedAnimeSources.changes() else flowOf(emptySet()),
    ) { manga, anime ->
        mapOf(ContentType.MANGA to manga, ContentType.ANIME to anime)
    }

    private suspend fun load(
        pinnedByType: Map<ContentType, Set<String>>,
        latest: Boolean,
    ): DiscoverRail = coroutineScope {
        val results = pinnedByType
            .flatMap { (type, ids) -> ids.mapNotNull { it.toLongOrNull() }.map { type to it } }
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
