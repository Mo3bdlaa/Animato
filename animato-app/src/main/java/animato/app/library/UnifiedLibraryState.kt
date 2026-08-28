package animato.app.library

import androidx.compose.runtime.Immutable
import animato.anime.content.EntryForm
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
    /**
     * Which shape of thing to show, or null for all of them.
     *
     * Single-valued rather than a fourth checkbox, because unlike the three above it these are
     * mutually exclusive — a title is a series or a film or a channel and never two of them, so a
     * row of "series ✓ films ✓ live ✗" is three controls describing one choice.
     *
     * ## Why it is not on the lens
     *
     * The lens asks *which library*, and it asks it of every screen at once: Home, Discover,
     * Updates, Downloads and the extension list all read it. Films and channels are not a library —
     * they are things inside one — and hanging them off the lens would put the question *where are
     * the films* to Discover and to the extension store, which have no answer to give. So it lives
     * where the other questions about what a title *is* already live.
     */
    val form: EntryForm? = null,
) {

    val any: Boolean get() = unviewedOnly || downloadedOnly || trackedOnly || form != null

    fun accepts(entry: LibraryEntry, downloaded: Boolean, tracked: Boolean, form: EntryForm): Boolean {
        if (unviewedOnly && entry.unviewedItems <= 0) return false
        if (downloadedOnly && !downloaded) return false
        if (trackedOnly && !tracked) return false
        if (this.form != null && this.form != form) return false
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
    /**
     * How many items each entry has on disk, for the entries that have any.
     *
     * A count rather than a flag because the card shows the number, and the download filter is the
     * same question asked less precisely — [downloadedEntryKeys] derives from this so the badge and
     * the filter cannot end up disagreeing about what is downloaded.
     */
    val downloadedCounts: Map<Pair<ContentType, Long>, Int> = emptyMap(),
    val trackedEntryKeys: Set<Pair<ContentType, Long>> = emptySet(),
    /**
     * The entries that are not ordinary serials, and what they are instead.
     *
     * Only those. Every extension in the ecosystem and the whole manga side have serials and
     * nothing else, so holding a value for each of them would be a map the size of the library
     * saying the same word over and over — [formOf] fills in the default. See `EntryForm`.
     */
    val entryForms: Map<Pair<ContentType, Long>, EntryForm> = emptyMap(),
    val categories: List<LibraryCategory> = emptyList(),
    val lens: ContentFilter = ContentFilter.ALL,
    val selectedCategory: String? = null,
    val filters: LibraryFilters = LibraryFilters(),
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_UPDATED,
    val columns: Int = UnifiedLibraryPreferences.DEFAULT_COLUMNS,
    val showUnviewedCount: Boolean = true,
    val searchQuery: String? = null,
) {

    /** The entries with anything on disk. Only ever the keys of [downloadedCounts]. */
    val downloadedEntryKeys: Set<Pair<ContentType, Long>> get() = downloadedCounts.keys

    /** How many items this entry has on disk, and zero rather than nothing when it has none. */
    fun downloadedCount(entry: LibraryEntry): Int =
        downloadedCounts[entry.contentType to entry.entryId] ?: 0

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
                filters.accepts(
                    entry = it,
                    downloaded = key in downloadedEntryKeys,
                    tracked = key in trackedEntryKeys,
                    form = formOf(it),
                )
            }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .sortedWith(sortMode.comparator())
            .toList()
    }

    /** What shape an entry is, with the assumption the whole app makes filled in. */
    fun formOf(entry: LibraryEntry): EntryForm =
        entryForms[entry.contentType to entry.entryId] ?: EntryForm.Serial

    /**
     * The shapes actually present, so the picker only offers what is there.
     *
     * A library with no channels in it should not offer a Live chip that empties the grid — the
     * row is a description of the collection before it is a control over it. Series is always
     * offered when anything at all is in the library, since anything not listed is one.
     */
    val availableForms: List<EntryForm> by lazy {
        val present = shelfEntries.mapTo(mutableSetOf()) { formOf(it) }
        // Plus whatever is currently chosen, even when this shelf has none of it. Otherwise
        // filtering to Live and then walking into a category of nothing but series takes the Live
        // chip away along with the only visible way to stop filtering by it.
        filters.form?.let(present::add)
        EntryForm.entries.filter { it in present }
    }

    /** Whether the grid is empty because of something that was set, rather than because it is. */
    val emptiedBySettings: Boolean
        get() = entries.isNotEmpty() && visibleEntries.isEmpty()
}
