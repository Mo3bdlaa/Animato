package animato.domain.content

import tachiyomi.domain.library.manga.LibraryManga

/**
 * Projects a [LibraryManga] onto [LibraryEntry].
 *
 * This exists as a wrapper rather than as `LibraryManga : LibraryEntry` on purpose. The manga side
 * of the codebase is kept byte-identical to upstream so their fixes keep applying cleanly; adapting
 * from the outside is what buys that. The anime side, which has no upstream, implements the
 * interface directly instead.
 */
@JvmInline
value class MangaLibraryEntry(val libraryManga: LibraryManga) : LibraryEntry {

    override val entryId: Long get() = libraryManga.id

    override val contentType: ContentType get() = ContentType.MANGA

    override val sourceId: Long get() = libraryManga.manga.source

    override val categoryId: Long get() = libraryManga.category

    override val title: String get() = libraryManga.manga.title

    override val thumbnailUrl: String? get() = libraryManga.manga.thumbnailUrl

    override val favorite: Boolean get() = libraryManga.manga.favorite

    override val dateAdded: Long get() = libraryManga.manga.dateAdded

    override val genre: List<String>? get() = libraryManga.manga.genre

    override val totalItems: Long get() = libraryManga.totalChapters

    override val viewedItems: Long get() = libraryManga.readCount

    override val bookmarkCount: Long get() = libraryManga.bookmarkCount

    override val latestUpload: Long get() = libraryManga.latestUpload

    override val lastViewed: Long get() = libraryManga.lastRead

    override val itemsFetchedAt: Long get() = libraryManga.chapterFetchedAt
}

fun LibraryManga.asLibraryEntry(): LibraryEntry = MangaLibraryEntry(this)
