package animato.app.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.viewmodel.StateViewModel
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

@Immutable
data class DiscoverState(
    val hasPinnedSources: Boolean = true,
    val popular: DiscoverRail = DiscoverRail(),
    val latest: DiscoverRail = DiscoverRail(),
)

/**
 * What to watch or read next, asked of the sources the user pinned.
 *
 * `docs/BRANDING.md` asks for a Trending rail. No extension exposes trending — the contract is
 * popular, latest and search, per source — so this is what the data supports: each pinned source's
 * own popular and latest pages, interleaved so no single source fills the rail.
 *
 * Every source is asked in parallel and every failure is contained: a source that is down, rate
 * limited or broken contributes nothing and is named, rather than emptying the rail or throwing.
 * That matters more here than anywhere else in the app, because this is the one screen that reaches
 * the network before the user has asked for anything.
 */
class DiscoverScreenModel(
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val animeSourcePreferences: AnimeSourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val contentType: ContentType,
) : StateViewModel<DiscoverState>(DiscoverState()) {

    init {
        viewModelScope.launch {
            pinnedSourceIds().collectLatest { pinned ->
                mutableState.value = DiscoverState(hasPinnedSources = pinned.isNotEmpty())
                if (pinned.isEmpty()) return@collectLatest

                launch {
                    val rail = load(pinned, latest = false)
                    mutableState.value = state.value.copy(popular = rail)
                }
                launch {
                    val rail = load(pinned, latest = true)
                    mutableState.value = state.value.copy(latest = rail)
                }
            }
        }
    }

    private fun pinnedSourceIds() = when (contentType) {
        ContentType.MANGA -> sourcePreferences.pinnedSources.changes()
        ContentType.ANIME -> animeSourcePreferences.pinnedAnimeSources.changes()
    }

    private suspend fun load(pinned: Set<String>, latest: Boolean): DiscoverRail = coroutineScope {
        val results = pinned
            .mapNotNull { it.toLongOrNull() }
            .map { sourceId -> async { fetch(sourceId, latest) } }
            .awaitAll()

        DiscoverRail(
            isLoading = false,
            items = results.mapNotNull { it.getOrNull() }.interleave().take(RAIL_LIMIT),
            failedSources = results.mapNotNull { it.exceptionOrNull()?.sourceName },
        )
    }

    private suspend fun fetch(sourceId: Long, latest: Boolean): Result<List<DiscoverItem>> {
        val sourceName = sourceNameOf(sourceId)
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

    private fun sourceNameOf(sourceId: Long): String = when (contentType) {
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
 * One from each source, then the next from each, and so on.
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
