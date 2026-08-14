package tachiyomi.domain.library.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.library.manga.model.MangaLibrarySort

@Execution(ExecutionMode.CONCURRENT)
class LibraryFlagsTest {

    @Test
    fun `Check the amount of flags`() {
        LibraryDisplayMode.values.size shouldBe 4
        MangaLibrarySort.types.size shouldBe 10
        MangaLibrarySort.directions.size shouldBe 2
    }

    @Test
    fun `Test Flag plus operator (LibrarySort)`() {
        val current = MangaLibrarySort(
            MangaLibrarySort.Type.LastRead,
            MangaLibrarySort.Direction.Ascending,
        )
        val new = MangaLibrarySort(
            MangaLibrarySort.Type.DateAdded,
            MangaLibrarySort.Direction.Ascending,
        )

        current + new shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator`() {
        val sort = MangaLibrarySort(
            MangaLibrarySort.Type.DateAdded,
            MangaLibrarySort.Direction.Ascending,
        )

        sort.flag shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator with old flag as base`() {
        val currentSort = MangaLibrarySort(
            MangaLibrarySort.Type.UnreadCount,
            MangaLibrarySort.Direction.Descending,
        )
        currentSort.flag shouldBe 0b00001100

        val sort = MangaLibrarySort(
            MangaLibrarySort.Type.DateAdded,
            MangaLibrarySort.Direction.Ascending,
        )
        val flag = currentSort.flag + sort

        flag shouldBe 0b01011100
        flag shouldNotBe currentSort.flag
    }

    @Test
    fun `Test default flags`() {
        val sort = MangaLibrarySort.default

        sort.type + sort.direction shouldBe 0b01000000
    }
}
