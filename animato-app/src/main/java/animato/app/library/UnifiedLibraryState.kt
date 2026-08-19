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

    /**
     * The shelf this one sits inside, read out of its own name.
     *
     * Sub-categories are the request; a `parent_id` column is the obvious way to hold them and is
     * not available. Categories live in two databases, one of which is Mihon's and not edited here,
     * so a schema change would land on one half and not the other.
     *
     * A separator in the name needs no schema at all. `Anime/Seasonal` is stored as exactly that
     * string, so a backup opened in Mihon or Aniyomi shows a category called `Anime/Seasonal` and
     * loses nothing — the hierarchy is drawn here rather than recorded anywhere. Somebody who never
     * types a slash sees precisely the interface they saw before.
     */
    val parent: String? get() = name.substringBefore(SEPARATOR, missingDelimiterValue = "").takeIf { it.isNotEmpty() }

    /** What to write on the chip once its parent is already on screen above it. */
    val leaf: String get() = name.substringAfter(SEPARATOR)

    /** The shelf this belongs to at the top level, which is itself when it has no parent. */
    val root: String get() = parent ?: name

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

    companion object {
        /**
         * One level, not a path. `Anime/Seasonal/Winter` reads as a category named
         * `Seasonal/Winter` inside `Anime`, because a chip row is one line and a tree three deep
         * has nowhere to go on a phone.
         */
        const val SEPARATOR = '/'
    }
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

    /** The top row: one chip per shelf, whether or not it has anything nested under it. */
    val rootCategories: List<String> by lazy {
        visibleCategories.map { it.root }.distinct()
    }

    /**
     * The second row, which exists only while standing in a shelf that has one.
     *
     * A parent that is only ever a parent — `Anime/Seasonal` with no bare `Anime` — still gets its
     * chip on the top row, and selecting it means the whole branch. So the sub-row never has to
     * carry an "all of this" chip: the chip above it already is one.
     */
    val childCategories: List<LibraryCategory> by lazy {
        val root = selectedCategory?.substringBefore(LibraryCategory.SEPARATOR) ?: return@lazy emptyList()
        visibleCategories.filter { it.parent == root }
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
     * Every category the selection stands for, which for a parent is the whole branch.
     *
     * Selecting `Anime` shows what is filed directly under it *and* everything in `Anime/Seasonal`,
     * because a shelf that hides the contents of its own sub-shelves is a worse answer than the
     * flat list it replaced.
     */
    val activeBranch: List<LibraryCategory> by lazy {
        val selected = selectedCategory ?: return@lazy emptyList()
        visibleCategories.filter {
            it.name == selected || it.name.startsWith(selected + LibraryCategory.SEPARATOR)
        }
    }

    /**
     * Everything on the selected shelf, before the derived filters and the search box.
     *
     * This is the number on the chip. It is the size of the shelf you are standing in — not the
     * count of what survived a checkbox, which would change under you as you tick things and make
     * the chip read as a result rather than a place.
     */
    val shelfEntries: List<LibraryEntry> by lazy {
        val branch = activeBranch
        entries
            .asSequence()
            .filter { lens.accepts(it.contentType) }
            .filter { branch.isEmpty() || branch.any { category -> category.accepts(it) } }
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
