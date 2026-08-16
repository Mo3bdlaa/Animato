package animato.app.library

import animato.domain.content.ContentFilter
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

    private val ongoing = LibraryCategory("Ongoing", mangaIds = setOf(10), animeIds = setOf(10))
    private val backlog = LibraryCategory("Backlog", mangaIds = setOf(11), animeIds = emptySet())

    @Test
    fun `the lens decides which half is listed at all`() {
        state(lens = ContentFilter.ANIME).visibleEntries.map { it.title } shouldBe
            listOf("Frieren", "Dandadan")
        state(lens = ContentFilter.MANGA).visibleEntries.map { it.title } shouldBe
            listOf("One Piece", "Kaiju No. 8")
    }

    @Test
    fun `a category chip is a name and answers for both tables`() {
        // Same id in both tables, which is the ordinary case: two shelves both called Ongoing.
        val scoped = state(categories = listOf(ongoing), selected = "Ongoing")
        scoped.visibleEntries.map { it.title } shouldBe
            listOf("One Piece", "Frieren", "Dandadan", "Kaiju No. 8")
    }

    @Test
    fun `a category with no ids on the lensed half is not offered`() {
        // Backlog exists only in the manga table, so under the anime lens it has nothing to say.
        state(categories = listOf(ongoing, backlog), lens = ContentFilter.ANIME)
            .visibleCategories.map { it.name } shouldBe listOf("Ongoing")
    }

    @Test
    fun `a selection the lens has hidden falls back to everything`() {
        val stranded = state(
            categories = listOf(ongoing, backlog),
            selected = "Backlog",
            lens = ContentFilter.ANIME,
        )
        stranded.activeCategory shouldBe null
        stranded.visibleEntries.map { it.title } shouldBe listOf("Frieren", "Dandadan")
    }

    @Test
    fun `unread only keeps what has something waiting`() {
        state(filters = LibraryFilters(unviewedOnly = true)).visibleEntries.map { it.title } shouldBe
            listOf("One Piece", "Dandadan")
    }

    @Test
    fun `downloaded reads the cache rather than the entry`() {
        val downloaded = state(
            filters = LibraryFilters(downloadedOnly = true),
            downloadedKeys = setOf(ContentType.ANIME to 3L),
        )
        downloaded.visibleEntries.map { it.title } shouldBe listOf("Dandadan")
    }

    @Test
    fun `tracked reads the track tables rather than the entry`() {
        val tracked = state(
            filters = LibraryFilters(trackedOnly = true),
            trackedKeys = setOf(ContentType.MANGA to 1L),
        )
        tracked.visibleEntries.map { it.title } shouldBe listOf("One Piece")
    }

    @Test
    fun `filters combine rather than replacing one another`() {
        val both = state(
            filters = LibraryFilters(unviewedOnly = true, downloadedOnly = true),
            downloadedKeys = setOf(ContentType.ANIME to 3L, ContentType.MANGA to 4L),
        )
        // Kaiju is downloaded but finished; Dandadan is both unread and downloaded.
        both.visibleEntries.map { it.title } shouldBe listOf("Dandadan")
    }

    @Test
    fun `the chip count is the shelf, not what survived the filters`() {
        val filtered = state(filters = LibraryFilters(unviewedOnly = true))
        filtered.shelfEntries.size shouldBe 4
        filtered.visibleEntries.size shouldBe 2
    }

    @Test
    fun `an empty grid distinguishes a filter from an empty library`() {
        state(filters = LibraryFilters(trackedOnly = true)).emptiedBySettings shouldBe true
        state(entries = emptyList()).emptiedBySettings shouldBe false
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

    private fun state(
        entries: List<LibraryEntry> = all,
        lens: ContentFilter = ContentFilter.ALL,
        categories: List<LibraryCategory> = emptyList(),
        selected: String? = null,
        filters: LibraryFilters = LibraryFilters(),
        sort: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
        searchQuery: String? = null,
        downloadedKeys: Set<Pair<ContentType, Long>> = emptySet(),
        trackedKeys: Set<Pair<ContentType, Long>> = emptySet(),
    ) = UnifiedLibraryState(
        isLoading = false,
        entries = entries,
        downloadedEntryKeys = downloadedKeys,
        trackedEntryKeys = trackedKeys,
        categories = categories,
        lens = lens,
        selectedCategory = selected,
        filters = filters,
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
