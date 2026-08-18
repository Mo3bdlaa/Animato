package animato.app.source

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.anime.model.Anime as DomainAnime
import tachiyomi.domain.manga.model.Manga as DomainManga

/** What the source is being asked for. Search is the third because it replaces the other two. */
enum class SourceListing { POPULAR, LATEST, SEARCH }

@Immutable
data class SourceBrowseItem(
    val entryId: Long,
    val title: String,
    val coverData: Any?,
    val favorite: Boolean,
)

@Immutable
data class SourceBrowseState(
    val sourceName: String = "",
    val supportsLatest: Boolean = false,
    val listing: SourceListing = SourceListing.POPULAR,
    val query: String = "",
    val items: List<SourceBrowseItem> = emptyList(),
    /** The first page, which is the only wait that owns the whole screen. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = true,
    /** The source's own words when a request failed, kept rather than swallowed. */
    val failure: String? = null,
    val mangaFilters: FilterList? = null,
    val animeFilters: AnimeFilterList? = null,
    /** The source's own site, for the pages an extension can no longer parse. */
    val webViewUrl: String? = null,
) {
    val hasFilters: Boolean
        get() = (mangaFilters?.isNotEmpty() ?: false) || (animeFilters?.isNotEmpty() ?: false)

    val isEmpty: Boolean get() = !isLoading && items.isEmpty() && failure == null
}

/**
 * Browsing one source, on this app's terms.
 *
 * Tapping an installed extension used to land on Mihon's browse screen or the ported anime one,
 * and a device listed what was wrong with arriving there: no pull to refresh, no way to sort, a
 * search that behaves unlike the app's own, and filters behind a control in an odd place. All four
 * are true, and none of them are fixable in those files — they are upstream's.
 *
 * So this is the screen, and it borrows exactly one thing from upstream: the filter sheet. A
 * source's filters are a tree of headers, groups, selects, sorts and tri-states that each extension
 * composes freely; upstream renders that tree correctly and it is pure UI over a public model.
 * Re-drawing it here would be a second dialect of the same idea and a new source of bugs.
 *
 * ## Paging without Paging3
 *
 * Upstream pages with the Paging library. This keeps a page counter and asks for the next one when
 * the grid gets near its end — because that is all a source offers: `getPopularManga(page)` and a
 * `hasNextPage` flag. The library would buy invalidation and caching that a screen with a
 * refreshable, filterable, single-source list does not use.
 *
 * ## Why every item goes through the local database
 *
 * A cover on this grid has to know whether it is already in the library, and tapping it has to open
 * a title page, which takes an id. Both come from `networkToLocal*`, which upserts the network
 * result and hands back the domain entry — the same path upstream uses, and the reason a source
 * result and a library entry are never two different objects for the same thing.
 */
class SourceBrowseScreenModel(
    private val sourceId: Long,
    private val contentType: ContentType,
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<SourceBrowseState>
        field = MutableStateFlow(SourceBrowseState())

    private var page = 1

    init {
        val mangaSource = sourceManager.get(sourceId) as? CatalogueSource
        val animeSource = animeSourceManager.get(sourceId) as? AnimeCatalogueSource
        state.update {
            when (contentType) {
                ContentType.MANGA -> it.copy(
                    sourceName = mangaSource?.name.orEmpty(),
                    supportsLatest = mangaSource?.supportsLatest == true,
                    mangaFilters = runCatching { mangaSource?.getFilterList() }.getOrNull(),
                    webViewUrl = (mangaSource as? HttpSource)?.baseUrl,
                )
                ContentType.ANIME -> it.copy(
                    sourceName = animeSource?.name.orEmpty(),
                    supportsLatest = animeSource?.supportsLatest == true,
                    animeFilters = runCatching { animeSource?.getFilterList() }.getOrNull(),
                    webViewUrl = (animeSource as? AnimeHttpSource)?.baseUrl,
                )
            }
        }
        reload()
    }

    fun selectListing(listing: SourceListing) {
        if (listing == state.value.listing && listing != SourceListing.SEARCH) return
        state.update { it.copy(listing = listing, query = if (listing == SourceListing.SEARCH) it.query else "") }
        reload()
    }

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
    }

    /** Runs the typed query. A blank one is a request to go back to the source's own front page. */
    fun search() {
        val query = state.value.query
        state.update { it.copy(listing = if (query.isBlank()) SourceListing.POPULAR else SourceListing.SEARCH) }
        reload()
    }

    fun setMangaFilters(filters: FilterList) = state.update { it.copy(mangaFilters = filters) }

    fun setAnimeFilters(filters: AnimeFilterList) = state.update { it.copy(animeFilters = filters) }

    /** Filters are a search with no words: the source decides what an empty query plus filters means. */
    fun applyFilters() {
        state.update { it.copy(listing = SourceListing.SEARCH) }
        reload()
    }

    fun resetFilters() {
        val mangaSource = sourceManager.get(sourceId) as? CatalogueSource
        val animeSource = animeSourceManager.get(sourceId) as? AnimeCatalogueSource
        state.update {
            it.copy(
                mangaFilters = runCatching { mangaSource?.getFilterList() }.getOrNull(),
                animeFilters = runCatching { animeSource?.getFilterList() }.getOrNull(),
            )
        }
    }

    /** Pull to refresh: the same request as now, from the first page. */
    fun reload() {
        page = 1
        state.update { it.copy(isLoading = true, items = emptyList(), hasNextPage = true, failure = null) }
        viewModelScope.launch { fetchPage(append = false) }
    }

    fun loadMore() {
        val current = state.value
        if (current.isLoading || current.isLoadingMore || !current.hasNextPage) return
        state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { fetchPage(append = true) }
    }

    private suspend fun fetchPage(append: Boolean) {
        val current = state.value
        val result = runCatching {
            withIOContext {
                when (contentType) {
                    ContentType.MANGA -> fetchManga(current)
                    ContentType.ANIME -> fetchAnime(current)
                }
            }
        }

        result
            .onSuccess { (fetched, hasNext) ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasNextPage = hasNext,
                        // Distinct by id: a source that ignores the page parameter would otherwise
                        // pile the same covers up forever as the grid asks for more.
                        items = if (append) (it.items + fetched).distinctBy { item -> item.entryId } else fetched,
                    )
                }
                if (fetched.isNotEmpty()) page++
            }
            .onFailure { error ->
                logcat(LogPriority.WARN, error) { "Browsing ${current.sourceName} failed" }
                state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        hasNextPage = false,
                        // Only when there is nothing on screen. A page five that fails should not
                        // replace four pages of results with an error.
                        failure = if (it.items.isEmpty()) error.message ?: error.javaClass.simpleName else null,
                    )
                }
            }
    }

    private suspend fun fetchManga(current: SourceBrowseState): Pair<List<SourceBrowseItem>, Boolean> {
        val source = sourceManager.get(sourceId) as? CatalogueSource ?: return emptyList<SourceBrowseItem>() to false
        val result = when (current.listing) {
            SourceListing.POPULAR -> source.getPopularManga(page)
            SourceListing.LATEST -> source.getLatestUpdates(page)
            SourceListing.SEARCH -> source.getSearchManga(
                page = page,
                query = current.query,
                filters = current.mangaFilters ?: FilterList(),
            )
        }
        val items = result.mangas.map { networkManga ->
            val local = networkToLocalManga(
                DomainManga.create().copy(url = networkManga.url, title = networkManga.title, source = sourceId),
            )
            // The network result carries the cover the source just served; the stored row may
            // predate it, so the fresher of the two wins.
            val withCover = local.copy(thumbnailUrl = networkManga.thumbnail_url ?: local.thumbnailUrl)
            SourceBrowseItem(
                entryId = withCover.id,
                title = withCover.title,
                coverData = withCover.asMangaCover(),
                favorite = withCover.favorite,
            )
        }
        return items to result.hasNextPage
    }

    private suspend fun fetchAnime(current: SourceBrowseState): Pair<List<SourceBrowseItem>, Boolean> {
        val source = animeSourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: return emptyList<SourceBrowseItem>() to false
        val result = when (current.listing) {
            SourceListing.POPULAR -> source.getPopularAnime(page)
            SourceListing.LATEST -> source.getLatestUpdates(page)
            SourceListing.SEARCH -> source.getSearchAnime(
                page = page,
                query = current.query,
                filters = current.animeFilters ?: AnimeFilterList(),
            )
        }
        val items = result.animes.map { networkAnime ->
            val local = networkToLocalAnime.await(
                DomainAnime.create().copy(url = networkAnime.url, title = networkAnime.title, source = sourceId),
            )
            val withCover = local.copy(thumbnailUrl = networkAnime.thumbnail_url ?: local.thumbnailUrl)
            SourceBrowseItem(
                entryId = withCover.id,
                title = withCover.title,
                coverData = withCover.asAnimeCover(),
                favorite = withCover.favorite,
            )
        }
        return items to result.hasNextPage
    }

    /**
     * The long press: into the library, or back out of it.
     *
     * No category dialog, for the same reason the title page's heart does not ask: this screen
     * answers for both halves and the two category pickers are per-half. The library's own quick
     * sheet is where an entry gets filed.
     */
    fun toggleFavorite(item: SourceBrowseItem) {
        val nowFavorite = !item.favorite
        state.update { current ->
            current.copy(
                items = current.items.map { if (it.entryId == item.entryId) it.copy(favorite = nowFavorite) else it },
            )
        }
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA -> updateManga.await(MangaUpdate(id = item.entryId, favorite = nowFavorite))
                ContentType.ANIME -> updateAnime.await(AnimeUpdate(id = item.entryId, favorite = nowFavorite))
            }
        }
    }
}
