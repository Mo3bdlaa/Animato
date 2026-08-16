package animato.app.library

import androidx.compose.runtime.Immutable
import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

/**
 * One chip in the row above the grid: a category, by name.
 *
 * ## Why a name and not an id
 *
 * Manga categories and anime categories are separate tables with separate id spaces, so "category
 * 3" is two different shelves depending on which half you ask. The chip row cannot show that and
 * stay a row — it would have to read *Ongoing · Manga* and *Ongoing · Anime* side by side, which is
 * the anime-annex shape the whole app is trying to leave behind.
 *
 * So a chip is a name, and it carries every id that answers to that name in either table. The
 * databases stay separate — nothing is merged, migrated or renamed — but the control above them
 * asks the question a person actually has: *show me the things I filed under Ongoing.* Someone who
 * deliberately keeps two unrelated shelves under the same name in the two halves gets them
 * together here; that is a fair reading of having given them the same name.
 */
@Immutable
data class LibraryCategory(
    val name: String,
    val mangaIds: Set<Long>,
    val animeIds: Set<Long>,
) {

    fun accepts(entry: LibraryEntry): Boolean {
        val ids = when (entry.contentType) {
            ContentType.MANGA -> mangaIds
            ContentType.ANIME -> animeIds
        }
        return entry.categoryIds.any { it in ids }
    }

    /** Whether this category has anything to say under [lens] at all. */
    fun visibleUnder(lens: ContentFilter): Boolean =
        (lens.includesManga && mangaIds.isNotEmpty()) ||
            (lens.includesAnime && animeIds.isNotEmpty())
}

/**
 * The derived states, which are not categories.
 *
 * A title can be unread *and* downloaded *and* tracked at once, so these cannot be a chip row — a
 * row of chips promises one at a time and a shelf you can point at. They are checkboxes in the
 * filter sheet, where several being on together reads as what it is.
 */
@Immutable
data class LibraryFilters(
    val unviewedOnly: Boolean = false,
    val downloadedOnly: Boolean = false,
    val trackedOnly: Boolean = false,
) {

    val any: Boolean get() = unviewedOnly || downloadedOnly || trackedOnly

    fun accepts(entry: LibraryEntry, downloaded: Boolean, tracked: Boolean): Boolean {
        if (unviewedOnly && entry.unviewedItems <= 0) return false
        if (downloadedOnly && !downloaded) return false
        if (trackedOnly && !tracked) return false
        return true
    }
}

/**
 * The sort options, in neutral words.
 *
 * No sort here says "chapter" or "episode": the grid holds both, and a control that names one
 * medium over a mixed list is what makes an app feel like two apps.
 */
enum class LibrarySortMode(val labelRes: StringResource) {
    RECENTLY_UPDATED(AYMR.strings.sort_recently_updated),
    RECENTLY_ADDED(MR.strings.action_sort_date_added),
    LAST_VIEWED(AYMR.strings.sort_recently_opened),
    TITLE(AYMR.strings.sort_title_az),
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
    val trackedEntryKeys: Set<Pair<ContentType, Long>> = emptySet(),
    val categories: List<LibraryCategory> = emptyList(),
    val lens: ContentFilter = ContentFilter.ALL,
    val selectedCategory: String? = null,
    val filters: LibraryFilters = LibraryFilters(),
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_UPDATED,
    val columns: Int = UnifiedLibraryPreferences.DEFAULT_COLUMNS,
    val showUnviewedCount: Boolean = true,
    val searchQuery: String? = null,
) {

    /** Categories worth drawing a chip for, in the order the two halves reported them. */
    val visibleCategories: List<LibraryCategory> by lazy {
        categories.filter { it.visibleUnder(lens) }
    }

    /**
     * The chip actually in effect.
     *
     * A selection survives a lens change only if it still means something: narrowing to Anime while
     * standing inside a manga-only category has to land somewhere, and All is the only landing
     * place that is not a lie.
     */
    val activeCategory: LibraryCategory? by lazy {
        visibleCategories.firstOrNull { it.name == selectedCategory }
    }

    /**
     * Everything on the selected shelf, before the derived filters and the search box.
     *
     * This is the number on the chip. It is the size of the shelf you are standing in — not the
     * count of what survived a checkbox, which would change under you as you tick things and make
     * the chip read as a result rather than a place.
     */
    val shelfEntries: List<LibraryEntry> by lazy {
        val category = activeCategory
        entries
            .asSequence()
            .filter { lens.accepts(it.contentType) }
            .filter { category == null || category.accepts(it) }
            .distinctBy { it.contentType to it.entryId }
            .toList()
    }

    /** The entries actually drawn, in order. */
    val visibleEntries: List<LibraryEntry> by lazy {
        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        shelfEntries
            .asSequence()
            .filter {
                val key = it.contentType to it.entryId
                filters.accepts(it, downloaded = key in downloadedEntryKeys, tracked = key in trackedEntryKeys)
            }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .sortedWith(sortMode.comparator())
            .toList()
    }

    /** Whether the grid is empty because of something that was set, rather than because it is. */
    val emptiedBySettings: Boolean
        get() = entries.isNotEmpty() && visibleEntries.isEmpty()
}
