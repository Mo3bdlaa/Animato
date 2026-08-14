package tachiyomi.domain.library.anime.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.library.model.plus

@Execution(ExecutionMode.CONCURRENT)
class AnimeLibraryFlagsTest {

    @Test
    fun `Check the amount of flags`() {
        AnimeLibrarySort.types.size shouldBe 11
        AnimeLibrarySort.directions.size shouldBe 2
    }

    @Test
    fun `Test Flag plus operator (LibrarySort)`() {
        val current = AnimeLibrarySort(
            AnimeLibrarySort.Type.LastSeen,
            AnimeLibrarySort.Direction.Ascending,
        )
        val new = AnimeLibrarySort(
            AnimeLibrarySort.Type.DateAdded,
            AnimeLibrarySort.Direction.Ascending,
        )

        current + new shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator`() {
        val sort = AnimeLibrarySort(
            AnimeLibrarySort.Type.DateAdded,
            AnimeLibrarySort.Direction.Ascending,
        )

        sort.flag shouldBe 0b01011100
    }

    @Test
    fun `Test Flag plus operator with old flag as base`() {
        val currentSort = AnimeLibrarySort(
            AnimeLibrarySort.Type.UnseenCount,
            AnimeLibrarySort.Direction.Descending,
        )
        currentSort.flag shouldBe 0b00001100

        val sort = AnimeLibrarySort(
            AnimeLibrarySort.Type.DateAdded,
            AnimeLibrarySort.Direction.Ascending,
        )
        val flag = sort.flag + sort

        flag shouldBe 0b01011100
        flag shouldNotBe currentSort.flag
    }

    @Test
    fun `Test default flags`() {
        val sort = AnimeLibrarySort.default

        sort.type + sort.direction shouldBe 0b01000000
    }
}
