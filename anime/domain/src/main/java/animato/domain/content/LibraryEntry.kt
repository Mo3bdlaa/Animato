package animato.domain.content

/**
 * A single library entry, independent of whether it is anime or manga.
 *
 * Anime and manga are stored in two separate databases with two parallel sets of models, so nothing
 * can join them at the SQL level. This interface is the seam that lets the UI treat them as one
 * list: both sides are projected onto a shared vocabulary and merged in memory.
 *
 * The vocabulary is deliberately neutral — "items" rather than episodes or chapters, "viewed"
 * rather than seen or read — so neither side's terminology leaks into shared code.
 *
 * Anime models implement this directly, because the anime half of the codebase is ours to shape.
 * Manga models are wrapped by [MangaLibraryEntry] instead, so that no file inherited from upstream
 * needs to change and updates can still be pulled in cleanly.
 */
interface LibraryEntry {

    val entryId: Long

    val contentType: ContentType

    val sourceId: Long

    /**
     * Every category this entry is in.
     *
     * A list rather than one id because manga can be in several at once, and the library groups by
     * category — an entry in three of them is drawn three times, once under each heading.
     */
    val categoryIds: List<Long>

    val title: String

    val thumbnailUrl: String?

    /**
     * What the image loader should be handed to draw this entry's cover.
     *
     * An `AnimeCover` or a `MangaCover`, passed through rather than converted: both already have a
     * fetcher registered, and each carries the entry id and last-modified stamp that make a custom
     * cover and a cache key work. Untyped because Mihon's `MangaCover` is upstream's file and
     * cannot be made to implement anything of ours.
     */
    val coverData: Any

    val favorite: Boolean

    val dateAdded: Long

    val genre: List<String>?

    /** Total episodes or chapters known for this entry. */
    val totalItems: Long

    /** Episodes seen or chapters read. */
    val viewedItems: Long

    val bookmarkCount: Long

    /** Upload time of the most recent item. */
    val latestUpload: Long

    /** When an item was last watched or read. */
    val lastViewed: Long

    /** When items were last fetched from the source. */
    val itemsFetchedAt: Long

    val unviewedItems: Long
        get() = totalItems - viewedItems

    val hasBookmarks: Boolean
        get() = bookmarkCount > 0

    val hasStarted: Boolean
        get() = viewedItems > 0
}
