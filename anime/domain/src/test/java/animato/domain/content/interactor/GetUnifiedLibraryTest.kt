package animato.domain.content.interactor

import animato.domain.content.ContentFilter
import animato.domain.content.ContentType
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository

@Execution(ExecutionMode.CONCURRENT)
class GetUnifiedLibraryTest {

    @Test
    fun `merges both libraries into one list`() = runTest {
        val entries = interactor(mangaCount = 2, animeCount = 3).subscribe().first()

        entries.size shouldBe 5
        entries.count { it.contentType == ContentType.MANGA } shouldBe 2
        entries.count { it.contentType == ContentType.ANIME } shouldBe 3
    }

    @Test
    fun `a filtered-out half is never read`() = runTest {
        var mangaRead = false
        var animeRead = false
        val interactor = interactor(
            mangaCount = 2,
            animeCount = 3,
            onMangaRead = { mangaRead = true },
            onAnimeRead = { animeRead = true },
        )

        val entries = interactor.subscribe(ContentFilter.ANIME).first()

        entries.size shouldBe 3
        animeRead shouldBe true
        mangaRead shouldBe false
    }

    @Test
    fun `manga entries carry every category they are in`() = runTest {
        val entries = interactor(mangaCount = 1, animeCount = 0).subscribe().first()

        entries.single().categoryIds shouldBe listOf(1L, 2L)
    }

    @Test
    fun `chapters and episodes are both counted as items`() = runTest {
        val entries = interactor(mangaCount = 1, animeCount = 1).subscribe().first()

        entries.map { it.totalItems to it.viewedItems } shouldBe listOf(20L to 5L, 12L to 4L)
        entries.map { it.unviewedItems } shouldBe listOf(15L, 8L)
    }

    private fun interactor(
        mangaCount: Int,
        animeCount: Int,
        onMangaRead: () -> Unit = {},
        onAnimeRead: () -> Unit = {},
    ): GetUnifiedLibrary {
        val mangaRepository = mockk<MangaRepository>()
        every { mangaRepository.getLibraryMangaAsFlow() } returns flow {
            onMangaRead()
            emit(List(mangaCount) { libraryManga(it.toLong()) })
        }
        val animeRepository = mockk<AnimeRepository>()
        every { animeRepository.getLibraryAnimeAsFlow() } returns flow {
            onAnimeRead()
            emit(List(animeCount) { libraryAnime(it.toLong()) })
        }
        return GetUnifiedLibrary(
            getLibraryManga = GetLibraryManga(mangaRepository),
            getLibraryAnime = GetLibraryAnime(animeRepository),
        )
    }

    private fun libraryManga(id: Long) = LibraryManga(
        manga = Manga.create().copy(id = id, title = "Manga $id"),
        categories = listOf(1L, 2L),
        totalChapters = 20,
        readCount = 5,
        bookmarkCount = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = 0,
    )

    private fun libraryAnime(id: Long) = LibraryAnime(
        anime = Anime.create().copy(id = id, title = "Anime $id"),
        category = 7L,
        totalCount = 12,
        seenCount = 4,
        bookmarkCount = 0,
        fillermarkCount = 0,
        latestUpload = 0,
        episodeFetchedAt = 0,
        lastSeen = 0,
    )
}
