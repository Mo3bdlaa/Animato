package animato.app.library

import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import animato.domain.content.interactor.GetUnifiedLibrary
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mihon.core.viewmodel.StateViewModel
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.interactor.GetCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One library, both content types.
 *
 * The chips filter by state and the scope filters by category, and they are separate controls
 * because they answer different questions — "what am I in the middle of" and "which shelf". Both
 * are applied in [UnifiedLibraryState], over a list that is already in memory, so changing either
 * costs no query.
 *
 * Whether an entry has downloads is not in the library row; it lives in a cache keyed by source and
 * title. That is why it is collected separately and joined by entry id, and why the download chip
 * updates when the cache does rather than when the library does.
 */
class UnifiedLibraryScreenModel(
    getUnifiedLibrary: GetUnifiedLibrary = Injekt.get(),
    getCategories: GetCategories = Injekt.get(),
    getAnimeCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
) : StateViewModel<UnifiedLibraryState>(UnifiedLibraryState()) {

    init {
        combine(
            getUnifiedLibrary.subscribe(),
            getCategories.subscribe(),
            getAnimeCategories.subscribe(),
            // Both already replay their latest value, so this does not wait for a download to change.
            downloadCache.changes,
            animeDownloadCache.changes,
        ) { entries, mangaCategories, animeCategories, _, _ ->
            val options = buildList {
                add(CategoryScopeOption(CategoryScope.All, name = "", contentType = null))
                mangaCategories.forEach {
                    add(CategoryScopeOption(CategoryScope.Manga(it.id), it.name, ContentType.MANGA))
                }
                animeCategories.forEach {
                    add(CategoryScopeOption(CategoryScope.Anime(it.id), it.name, ContentType.ANIME))
                }
            }
            entries to options
        }
            .onEach { (entries, options) ->
                val downloaded = withIOContext { entries.filterDownloaded() }
                mutableState.value = state.value.copy(
                    isLoading = false,
                    entries = entries,
                    downloadedEntryKeys = downloaded,
                    categoryOptions = options,
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Which entries have at least one item on disk.
     *
     * Both caches answer from an index they hold in memory, so this is a lookup per entry rather
     * than a directory walk — but there is one per library entry, which is why it runs off the main
     * thread.
     */
    private fun List<LibraryEntry>.filterDownloaded(): Set<Pair<ContentType, Long>> =
        mapNotNullTo(mutableSetOf()) { entry ->
            val count = when (entry) {
                is animato.domain.content.MangaLibraryEntry ->
                    downloadCache.getDownloadCount(entry.libraryManga.manga)
                is tachiyomi.domain.library.anime.LibraryAnime ->
                    animeDownloadCache.getDownloadCount(entry.anime)
                else -> 0
            }
            (entry.contentType to entry.entryId).takeIf { count > 0 }
        }

    fun setStatusFilter(filter: LibraryStatusFilter) {
        mutableState.value = state.value.copy(statusFilter = filter)
    }

    fun setCategoryScope(scope: CategoryScope) {
        mutableState.value = state.value.copy(categoryScope = scope)
    }

    fun setSortMode(mode: LibrarySortMode) {
        mutableState.value = state.value.copy(sortMode = mode)
    }

    fun search(query: String?) {
        mutableState.value = state.value.copy(searchQuery = query)
    }
}
