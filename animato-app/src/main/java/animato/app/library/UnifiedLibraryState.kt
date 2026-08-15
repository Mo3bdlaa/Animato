package animato.app.library

import androidx.compose.runtime.Immutable
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The chip row above the unified library.
 *
 * These are states, not categories: what someone is in the middle of, what they have finished,
 * what is waiting for them. Categories answer a different question and get their own control.
 *
 * [READING] and [WATCHING] are the same state on the two content types, kept apart because that is
 * how people talk about them and because it is the fastest way to see one library at a time.
 *
 * `docs/BRANDING.md` also lists a `Paused` chip. Nothing in either database records a paused entry
 * — that is a tracker's status, not the library's — so it is absent rather than always empty, and
 * arrives with the tracking work.
 */
enum class LibraryStatusFilter(val labelRes: StringResource) {
    ALL(AYMR.strings.label_all),
    READING(MR.strings.reading),
    WATCHING(AYMR.strings.watching),
    COMPLETED(MR.strings.completed),
    UNREAD(MR.strings.action_filter_unread),
    DOWNLOADED(MR.strings.label_downloaded),
    ;

    /**
     * Whether an entry belongs under this chip.
     *
     * [downloaded] is passed in rather than read off the entry: whether anything is downloaded
     * lives in a cache keyed by source and title, not in the library row.
     */
    fun accepts(entry: LibraryEntry, downloaded: Boolean): Boolean = when (this) {
        ALL -> true
        READING -> entry.contentType == ContentType.MANGA && entry.hasStarted && !entry.isFinished
        WATCHING -> entry.contentType == ContentType.ANIME && entry.hasStarted && !entry.isFinished
        COMPLETED -> entry.isFinished
        UNREAD -> entry.unviewedItems > 0
        DOWNLOADED -> downloaded
    }
}

/** Everything read, and something to have read — an entry with no items yet is not finished. */
private val LibraryEntry.isFinished: Boolean
    get() = totalItems > 0 && viewedItems >= totalItems

/**
 * Which categories the grid is scoped to.
 *
 * Manga categories and anime categories are separate tables with separate id spaces, so a scope
 * has to say which table it means. They are deliberately not merged by name: two categories that
 * happen to share a name are not necessarily the same shelf, and a rule that silently joins them
 * would split again the moment one is renamed.
 */
@Immutable
sealed interface CategoryScope {

    data object All : CategoryScope

    data class Manga(val id: Long) : CategoryScope

    data class Anime(val id: Long) : CategoryScope

    fun accepts(entry: LibraryEntry): Boolean = when (this) {
        All -> true
        is Manga -> entry.contentType == ContentType.MANGA && id in entry.categoryIds
        is Anime -> entry.contentType == ContentType.ANIME && id in entry.categoryIds
    }
}

/** One entry of the category picker: a scope, and what to call it. */
@Immutable
data class CategoryScopeOption(
    val scope: CategoryScope,
    val name: String,
    val contentType: ContentType?,
)

enum class LibrarySortMode(val labelRes: StringResource) {
    RECENTLY_UPDATED(MR.strings.action_sort_latest_chapter),
    RECENTLY_ADDED(MR.strings.action_sort_date_added),
    LAST_VIEWED(MR.strings.action_sort_last_read),
    TITLE(MR.strings.action_sort_alpha),
    UNVIEWED_COUNT(MR.strings.action_sort_unread_count),
    ;

    fun comparator(): Comparator<LibraryEntry> = when (this) {
        RECENTLY_UPDATED -> compareByDescending { it.latestUpload }
        RECENTLY_ADDED -> compareByDescending { it.dateAdded }
        LAST_VIEWED -> compareByDescending { it.lastViewed }
        TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        UNVIEWED_COUNT -> compareByDescending { it.unviewedItems }
    }
}

@Immutable
data class UnifiedLibraryState(
    val isLoading: Boolean = true,
    val entries: List<LibraryEntry> = emptyList(),
    val downloadedEntryKeys: Set<Pair<ContentType, Long>> = emptySet(),
    val categoryOptions: List<CategoryScopeOption> = emptyList(),
    val statusFilter: LibraryStatusFilter = LibraryStatusFilter.ALL,
    val categoryScope: CategoryScope = CategoryScope.All,
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_UPDATED,
    val searchQuery: String? = null,
) {

    /** The entries actually drawn, in order. */
    val visibleEntries: List<LibraryEntry> by lazy {
        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        entries
            .asSequence()
            .filter { categoryScope.accepts(it) }
            .filter { statusFilter.accepts(it, downloaded = (it.contentType to it.entryId) in downloadedEntryKeys) }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .distinctBy { it.contentType to it.entryId }
            .sortedWith(sortMode.comparator())
            .toList()
    }
}
