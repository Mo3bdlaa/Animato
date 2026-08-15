package animato.app.library

import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class UnifiedLibraryStateTest {

    private val onePieceInProgress = entry(1, ContentType.MANGA, "One Piece", total = 1100, viewed = 900)
    private val frierenFinished = entry(2, ContentType.ANIME, "Frieren", total = 28, viewed = 28)
    private val dandadanUnstarted = entry(3, ContentType.ANIME, "Dandadan", total = 12, viewed = 0)
    private val kaijuFinished = entry(4, ContentType.MANGA, "Kaiju No. 8", total = 120, viewed = 120)

    private val all = listOf(onePieceInProgress, frierenFinished, dandadanUnstarted, kaijuFinished)

    @Test
    fun `reading is manga in progress, watching is anime in progress`() {
        titles(LibraryStatusFilter.READING) shouldBe listOf("One Piece")
        titles(LibraryStatusFilter.WATCHING) shouldBe emptyList()

        val started = state(
            entries = listOf(entry(5, ContentType.ANIME, "Chainsaw Man", total = 12, viewed = 3)),
            filter = LibraryStatusFilter.WATCHING,
        )
        started.visibleEntries.map { it.title } shouldBe listOf("Chainsaw Man")
    }

    @Test
    fun `completed means everything viewed, and needs something to have viewed`() {
        titles(LibraryStatusFilter.COMPLETED) shouldBe listOf("Frieren", "Kaiju No. 8")

        val empty = state(
            entries = listOf(entry(6, ContentType.MANGA, "Just added", total = 0, viewed = 0)),
            filter = LibraryStatusFilter.COMPLETED,
        )
        empty.visibleEntries shouldBe emptyList()
    }

    @Test
    fun `downloaded reads the cache rather than the entry`() {
        val downloaded = state(
            filter = LibraryStatusFilter.DOWNLOADED,
            downloadedKeys = setOf(ContentType.ANIME to 3L),
        )
        downloaded.visibleEntries.map { it.title } shouldBe listOf("Dandadan")
    }

    @Test
    fun `a category scope only accepts its own library`() {
        val mangaScope = state(categoryScope = CategoryScope.Manga(10))
        mangaScope.visibleEntries.map { it.title } shouldBe listOf("One Piece", "Kaiju No. 8")

        // The same id in the other table is a different shelf and matches nothing here.
        val animeScope = state(categoryScope = CategoryScope.Anime(10))
        animeScope.visibleEntries.map { it.title } shouldBe listOf("Frieren", "Dandadan")
    }

    @Test
    fun `search matches titles across both libraries`() {
        state(searchQuery = "an").visibleEntries.map { it.title } shouldBe listOf("Dandadan")
    }

    @Test
    fun `sorting by title ignores case and mixes the two libraries`() {
        state(sort = LibrarySortMode.TITLE).visibleEntries.map { it.title } shouldBe
            listOf("Dandadan", "Frieren", "Kaiju No. 8", "One Piece")
    }

    @Test
    fun `an entry in two categories is drawn once`() {
        val twice = entry(7, ContentType.MANGA, "Berserk", total = 10, viewed = 1, categories = listOf(10, 11))
        state(entries = listOf(twice, twice)).visibleEntries.size shouldBe 1
    }

    private fun titles(filter: LibraryStatusFilter) = state(filter = filter).visibleEntries.map { it.title }

    private fun state(
        entries: List<LibraryEntry> = all,
        filter: LibraryStatusFilter = LibraryStatusFilter.ALL,
        categoryScope: CategoryScope = CategoryScope.All,
        sort: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
        searchQuery: String? = null,
        downloadedKeys: Set<Pair<ContentType, Long>> = emptySet(),
    ) = UnifiedLibraryState(
        isLoading = false,
        entries = entries,
        downloadedEntryKeys = downloadedKeys,
        statusFilter = filter,
        categoryScope = categoryScope,
        sortMode = sort,
        searchQuery = searchQuery,
    )

    private fun entry(
        id: Long,
        type: ContentType,
        title: String,
        total: Long,
        viewed: Long,
        categories: List<Long> = listOf(10),
    ) = object : LibraryEntry {
        override val entryId = id
        override val contentType = type
        override val sourceId = 0L
        override val categoryIds = categories
        override val title = title
        override val thumbnailUrl: String? = null
        override val coverData: Any = Unit
        override val favorite = true

        // Descending date-added keeps the declaration order, so tests that do not care about
        // sorting read in the order they were written.
        override val dateAdded = -id
        override val genre: List<String>? = null
        override val totalItems = total
        override val viewedItems = viewed
        override val bookmarkCount = 0L
        override val latestUpload = 0L
        override val lastViewed = 0L
        override val itemsFetchedAt = 0L
    }
}
