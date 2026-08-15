package tachiyomi.domain.library.anime

import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover

data class LibraryAnime(
    val anime: Anime,
    val category: Long,
    val totalCount: Long,
    val seenCount: Long,
    override val bookmarkCount: Long,
    val fillermarkCount: Long,
    override val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
) : LibraryEntry {
    val id: Long = anime.id

    val unseenCount
        get() = totalCount - seenCount

    override val hasBookmarks
        get() = bookmarkCount > 0

    override val hasStarted = seenCount > 0

    // LibraryEntry — implemented directly rather than through an adapter, since the anime half of
    // the codebase has no upstream to stay compatible with.

    override val entryId: Long get() = id

    override val contentType: ContentType get() = ContentType.ANIME

    override val sourceId: Long get() = anime.source

    // The anime query returns one row per category, so this is always a single id.
    override val categoryIds: List<Long> get() = listOf(category)

    override val title: String get() = anime.title

    override val thumbnailUrl: String? get() = anime.thumbnailUrl

    override val coverData: Any get() = anime.asAnimeCover()

    override val favorite: Boolean get() = anime.favorite

    override val dateAdded: Long get() = anime.dateAdded

    override val genre: List<String>? get() = anime.genre

    override val totalItems: Long get() = totalCount

    override val viewedItems: Long get() = seenCount

    override val lastViewed: Long get() = lastSeen

    override val itemsFetchedAt: Long get() = episodeFetchedAt
}
