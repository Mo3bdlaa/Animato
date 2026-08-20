package animato.app.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.content.EntryForm
import animato.anime.content.KnowsEntryForm
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import animato.domain.content.interactor.GetUnifiedLibrary
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.repository.TrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One library, both content types.
 *
 * The screen has three controls and they answer three different questions. The **lens** asks which
 * half of the collection — it is global, lives in the top bar, and is not this screen's to own. The
 * **category chips** ask which shelf. The **filter sheet** asks what state a title is in. Keeping
 * them apart is the whole design: a control that mixes "anime" with "unread" with "Ongoing" is the
 * one Aniyomi had, and nobody could predict what it would do.
 *
 * All three are applied in [UnifiedLibraryState] over a list already in memory, so changing any of
 * them costs no query.
 *
 * Two things a library row does not know about itself are joined in here. Whether anything is
 * downloaded lives in a cache keyed by source and title; whether anything is tracked lives in the
 * track tables. Both are collected separately and matched by entry id, which is why those two
 * filters update when their own source changes rather than when the library does.
 */
class UnifiedLibraryScreenModel(
    getUnifiedLibrary: GetUnifiedLibrary = Injekt.get(),
    getCategories: GetCategories = Injekt.get(),
    getAnimeCategories: GetVisibleAnimeCategories = Injekt.get(),
    trackRepository: TrackRepository = Injekt.get(),
    animeTrackRepository: AnimeTrackRepository = Injekt.get(),
    private val contentPreferences: ContentPreferences = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
    private val preferences: UnifiedLibraryPreferences = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val quickActions: LibraryQuickActions = LibraryQuickActions(),
) : ViewModel() {

    val quickSheet: MutableStateFlow<QuickSheetState?> = MutableStateFlow(null)

    val state: StateFlow<UnifiedLibraryState>
        field = MutableStateFlow<UnifiedLibraryState>(
            UnifiedLibraryState(
                lens = contentPreferences.contentFilter.get(),
                sortMode = preferences.sortMode.get(),
                filters = LibraryFilters(
                    unviewedOnly = preferences.unviewedOnly.get(),
                    downloadedOnly = preferences.downloadedOnly.get(),
                    trackedOnly = preferences.trackedOnly.get(),
                    form = EntryForm.entries.firstOrNull { it.name == preferences.formFilter.get() },
                ),
                columns = preferences.columns.get(),
                showUnviewedCount = preferences.showUnviewedCount.get(),
            ),
        )

    init {
        val chipsFlow = combine(
            getCategories.subscribe(),
            getAnimeCategories.subscribe(),
        ) { mangaCategories, animeCategories ->
            mergeCategories(
                // The uncategorised pseudo-category is not a shelf anyone filed anything under.
                manga = mangaCategories.filterNot { it.isSystemCategory }.map { it.name to it.id },
                anime = animeCategories.filterNot { it.isSystemCategory }.map { it.name to it.id },
            )
        }

        val trackedFlow = combine(
            trackRepository.getTracksAsFlow(),
            animeTrackRepository.getAnimeTracksAsFlow(),
        ) { mangaTracks, animeTracks ->
            buildSet {
                mangaTracks.forEach { add(ContentType.MANGA to it.mangaId) }
                animeTracks.forEach { add(ContentType.ANIME to it.animeId) }
            }
        }

        // Both already replay their latest value, so this does not wait for a download to change.
        val downloadsFlow = combine(downloadCache.changes, animeDownloadCache.changes) { _, _ -> }

        combine(
            getUnifiedLibrary.subscribe(),
            chipsFlow,
            trackedFlow,
            downloadsFlow,
        ) { entries, categories, tracked, _ ->
            LibrarySnapshot(entries, categories, tracked)
        }
            .onEach { snapshot ->
                val downloaded = withIOContext { snapshot.entries.filterDownloaded() }
                val forms = withIOContext { snapshot.entries.formsByEntry() }
                state.value = state.value.copy(
                    isLoading = false,
                    entries = snapshot.entries,
                    entryForms = forms,
                    downloadedEntryKeys = downloaded,
                    trackedEntryKeys = snapshot.tracked,
                    categories = snapshot.categories,
                )
            }
            .launchIn(viewModelScope)

        contentPreferences.contentFilter.changes()
            .onEach { lens -> state.value = state.value.copy(lens = lens) }
            .launchIn(viewModelScope)
    }

    private data class LibrarySnapshot(
        val entries: List<LibraryEntry>,
        val categories: List<LibraryCategory>,
        val tracked: Set<Pair<ContentType, Long>>,
    )

    /**
     * One chip per category name, carrying every id that answers to it.
     *
     * Manga order first, then anime names not already seen, so an install with categories in only
     * one half gets exactly that half's order and an install with both gets a stable one.
     */
    private fun mergeCategories(
        manga: List<Pair<String, Long>>,
        anime: List<Pair<String, Long>>,
    ): List<LibraryCategory> {
        val mangaIds = mutableMapOf<String, MutableSet<Long>>()
        val animeIds = mutableMapOf<String, MutableSet<Long>>()
        manga.forEach { (name, id) -> mangaIds.getOrPut(name) { mutableSetOf() } += id }
        anime.forEach { (name, id) -> animeIds.getOrPut(name) { mutableSetOf() } += id }

        val names = manga.map { it.first }.distinct() + anime.map { it.first }.distinct()
        return names.distinct().map { name ->
            LibraryCategory(
                name = name,
                mangaIds = mangaIds[name].orEmpty(),
                animeIds = animeIds[name].orEmpty(),
            )
        }
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

    /**
     * What shape each entry is, for the ones that are not the ordinary kind.
     *
     * The source is asked, not the entry — see `KnowsEntryForm`. Sources are resolved once each
     * rather than once per entry: a playlist with four hundred channels in the library is four
     * hundred rows sharing one source, and the answer for all of them is the same word.
     *
     * Serials are left out, so a library of nothing but extensions produces an empty map and this
     * costs a pass over a list. Off the main thread because it is one, and it runs whenever the
     * library changes.
     */
    private fun List<LibraryEntry>.formsByEntry(): Map<Pair<ContentType, Long>, EntryForm> {
        val knowers = mutableMapOf<Long, KnowsEntryForm?>()
        return buildMap {
            this@formsByEntry.forEach { entry ->
                // Manga has one shape and no source that says otherwise.
                if (entry.contentType != ContentType.ANIME) return@forEach
                val source = knowers.getOrPut(entry.sourceId) {
                    animeSourceManager.get(entry.sourceId) as? KnowsEntryForm
                } ?: return@forEach
                val form = runCatching { source.formOf(entry.url) }.getOrDefault(EntryForm.Serial)
                if (form != EntryForm.Serial) put(entry.contentType to entry.entryId, form)
            }
        }
    }

    /**
     * The same refresh the Updates screen runs: ask both libraries for anything new, per the
     * lens. Each job refuses if already running; started means either half accepted.
     */
    fun refresh(): Boolean {
        val context = Injekt.get<android.app.Application>()
        val lens = contentPreferences.contentFilter.get()
        val manga = lens.includesManga && eu.kanade.tachiyomi.data.library.LibraryUpdateJob.startNow(context)
        val anime = lens.includesAnime && eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob.startNow(context)
        return manga || anime
    }

    /**
     * The long-press sheet, which opens empty and fills in.
     *
     * Its captions state consequences — which chapter is next, how many rows a mark-done would
     * change, how many downloads a removal keeps — and none of that is in the library row. So the
     * sheet appears immediately and reads afterwards, rather than the press appearing to do nothing
     * while a query runs.
     */

    fun openQuickSheet(entry: LibraryEntry) {
        quickSheet.value = QuickSheetState(entry = entry)
        viewModelScope.launch {
            val loaded = quickActions.inspect(entry)
            if (quickSheet.value?.entry?.entryId == entry.entryId) quickSheet.value = loaded
        }
    }

    fun closeQuickSheet() {
        quickSheet.value = null
    }

    fun markDoneUpToHere(entry: LibraryEntry) {
        closeQuickSheet()
        viewModelScope.launch { quickActions.markDoneUpToHere(entry) }
    }

    fun downloadNext(entry: LibraryEntry) {
        closeQuickSheet()
        viewModelScope.launch { quickActions.downloadNext(entry, LibraryQuickActions.DOWNLOAD_BATCH) }
    }

    fun remove(entry: LibraryEntry, deleteDownloads: Boolean) {
        closeQuickSheet()
        viewModelScope.launch { quickActions.remove(entry, deleteDownloads) }
    }

    /** Tapping the chip you are already on goes back to All, the way a toggle does. */
    fun selectCategory(name: String?) {
        state.value = state.value.copy(
            selectedCategory = name?.takeIf { it != state.value.selectedCategory },
        )
    }

    fun setSortMode(mode: LibrarySortMode) {
        preferences.sortMode.set(mode)
        state.value = state.value.copy(sortMode = mode)
    }

    fun setFilters(filters: LibraryFilters) {
        preferences.unviewedOnly.set(filters.unviewedOnly)
        preferences.downloadedOnly.set(filters.downloadedOnly)
        preferences.trackedOnly.set(filters.trackedOnly)
        preferences.formFilter.set(filters.form?.name.orEmpty())
        state.value = state.value.copy(filters = filters)
    }

    fun setColumns(columns: Int) {
        preferences.columns.set(columns)
        state.value = state.value.copy(columns = columns)
    }

    fun setShowUnviewedCount(show: Boolean) {
        preferences.showUnviewedCount.set(show)
        state.value = state.value.copy(showUnviewedCount = show)
    }

    /** Everything the sheet can change, back to how it shipped. The lens is not the sheet's. */
    fun resetDisplay() {
        setSortMode(LibrarySortMode.RECENTLY_UPDATED)
        setFilters(LibraryFilters())
        setColumns(UnifiedLibraryPreferences.DEFAULT_COLUMNS)
        setShowUnviewedCount(true)
    }

    fun search(query: String?) {
        state.value = state.value.copy(searchQuery = query)
    }
}
