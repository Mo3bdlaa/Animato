package animato.domain.content

import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.asMangaCover

/**
 * A manga library row, seen as a [LibraryEntry].
 *
 * The anime models implement the interface directly; the manga ones cannot, because they are
 * upstream's and editing them is the thing this project exists not to do. So manga arrives through
 * a wrapper instead, which costs one allocation per row and keeps `LibraryManga` mergeable.
 *
 * The mapping is only renaming — chapters are items, read is viewed — with no behaviour of its own.
 * If a field ever needs computing rather than forwarding, it belongs in the interactor that builds
 * the list, not here.
 */
@JvmInline
value class MangaLibraryEntry(val libraryManga: LibraryManga) : LibraryEntry {

    override val entryId: Long get() = libraryManga.manga.id

    override val contentType: ContentType get() = ContentType.MANGA

    override val sourceId: Long get() = libraryManga.manga.source

    override val categoryIds: List<Long> get() = libraryManga.categories

    override val title: String get() = libraryManga.manga.title

    override val thumbnailUrl: String? get() = libraryManga.manga.thumbnailUrl

    override val coverData: Any get() = libraryManga.manga.asMangaCover()

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
