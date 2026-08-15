package animato.anime.backup

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * What a backup written by another app has to survive being read here.
 *
 * The writers below are declared in the test rather than reused from the source, on purpose. They
 * are what Aniyomi and Mihon actually put on disk, and if the reading models are renumbered by
 * accident the test has to fail — which it cannot do if both sides share one declaration.
 */
@Execution(ExecutionMode.CONCURRENT)
class AniyomiBackupFormatTest {

    @Test
    fun `reads the layout every released Aniyomi wrote`() {
        val bytes = encode(
            LegacyAniyomiWriter.serializer(),
            LegacyAniyomiWriter(
                backupManga = listOf(BackupManga(source = 1L, url = "/manga", title = "A manga")),
                backupCategories = listOf(BackupCategory(name = "Reading", order = 0)),
                backupAnime = listOf(anime(url = "/anime", title = "An anime")),
                backupAnimeCategories = listOf(BackupCategory(name = "Watching", order = 0)),
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                backupAnimeExtensionStores = listOf(WriterStore(indexUrl = "https://anime.example")),
                backupMangaExtensionRepos = listOf(WriterRepo(baseUrl = "https://manga.example")),
            ),
        )

        val backup = AniyomiBackupFormat.decode(bytes)

        backup.anime.map { it.title } shouldBe listOf("An anime")
        backup.animeCategories.map { it.name } shouldBe listOf("Watching")
        backup.animeSources.map { it.sourceId } shouldBe listOf(9L)
        backup.animeExtensionStores.map { it.indexUrl } shouldBe listOf("https://anime.example")
        backup.manga.map { it.title } shouldBe listOf("A manga")
        backup.mangaCategories.map { it.name } shouldBe listOf("Reading")
        backup.mangaExtensionStores.map { it.baseUrl } shouldBe listOf("https://manga.example")
    }

    @Test
    fun `reads the layout Aniyomi writes now`() {
        val bytes = encode(
            AniyomiWriter.serializer(),
            AniyomiWriter(
                backupManga = listOf(BackupManga(source = 1L, url = "/manga", title = "A manga")),
                isLegacy = false,
                backupAnime = listOf(anime(url = "/anime", title = "An anime")),
                backupAnimeCategories = listOf(BackupCategory(name = "Watching", order = 0)),
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                backupAnimeExtensionStores = listOf(WriterStore(indexUrl = "https://anime.example")),
                backupMangaExtensionRepos = listOf(WriterRepo(baseUrl = "https://manga.example")),
                backupCustomButtons = listOf(WriterButton(name = "Skip intro")),
            ),
        )

        val backup = AniyomiBackupFormat.decode(bytes)

        backup.anime.map { it.title } shouldBe listOf("An anime")
        backup.animeCategories.map { it.name } shouldBe listOf("Watching")
        backup.animeExtensionStores.map { it.indexUrl } shouldBe listOf("https://anime.example")
        backup.customButtons.map { it.name } shouldBe listOf("Skip intro")
        backup.manga.map { it.title } shouldBe listOf("A manga")
        backup.mangaExtensionStores.map { it.baseUrl } shouldBe listOf("https://manga.example")
    }

    /**
     * A Mihon backup is not an Aniyomi backup, and reading one here has to do no harm.
     *
     * Mihon's extension stores are at the number the current Aniyomi layout also uses, and the two
     * shapes line up field for field, so they come across. Nothing invents an anime.
     */
    @Test
    fun `reads a Mihon backup as a backup with no anime in it`() {
        val bytes = encode(
            MihonWriter.serializer(),
            MihonWriter(
                backupManga = listOf(BackupManga(source = 1L, url = "/manga", title = "A manga")),
                backupCategories = listOf(BackupCategory(name = "Reading", order = 0)),
                backupExtensionStores = listOf(
                    BackupExtensionStore(
                        indexUrl = "https://manga.example",
                        name = "Store",
                        badgeLabel = null,
                        signingKey = "key",
                        contactWebsite = "https://manga.example/about",
                        contactDiscord = null,
                        isLegacy = false,
                        extensionListUrl = null,
                    ),
                ),
            ),
        )

        val backup = AniyomiBackupFormat.decode(bytes)

        backup.anime.shouldBeEmpty()
        backup.animeCategories.shouldBeEmpty()
        backup.manga.map { it.title } shouldBe listOf("A manga")
        backup.mangaCategories.map { it.name } shouldBe listOf("Reading")
        backup.mangaExtensionStores.map { it.baseUrl } shouldBe listOf("https://manga.example")
        backup.mangaExtensionStores.map { it.signingKeyFingerprint } shouldBe listOf("key")
    }

    /**
     * The collision this whole reader exists for.
     *
     * In the old layout, 106 holds extension apks. In a Mihon backup it holds extension stores.
     * Reading one as the other is what makes Mihon reject an Aniyomi backup outright, and the test
     * is that an Aniyomi backup carrying apks still reads, and does not grow a repository out of
     * one.
     */
    @Test
    fun `does not read extension apks as a repository`() {
        val bytes = encode(
            LegacyAniyomiWriter.serializer(),
            LegacyAniyomiWriter(
                backupAnime = listOf(anime(url = "/anime", title = "An anime")),
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                backupExtensions = listOf(
                    WriterExtension(pkgName = "com.example.ext", apk = byteArrayOf(0x50, 0x4B, 0x03, 0x04)),
                ),
                backupMangaExtensionRepos = listOf(WriterRepo(baseUrl = "https://manga.example")),
            ),
        )

        val backup = AniyomiBackupFormat.decode(bytes)

        backup.anime.map { it.title } shouldBe listOf("An anime")
        backup.mangaExtensionStores.map { it.baseUrl } shouldBe listOf("https://manga.example")
    }

    @Test
    fun `keeps what an anime is made of`() {
        val bytes = encode(
            LegacyAniyomiWriter.serializer(),
            LegacyAniyomiWriter(
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                backupAnime = listOf(
                    anime(url = "/anime", title = "An anime").copy(
                        source = 9L,
                        favorite = true,
                        viewerFlags = 42,
                        version = 3L,
                        backgroundUrl = "https://example/bg.jpg",
                        seasonNumber = 2.0,
                        categories = listOf(0L),
                        episodes = listOf(
                            WriterEpisode(
                                url = "/anime/1",
                                name = "Episode 1",
                                seen = true,
                                lastSecondSeen = 1_200L,
                                totalSeconds = 1_400L,
                                episodeNumber = 1F,
                                fillermark = true,
                                summary = "A summary",
                                previewUrl = "https://example/preview.jpg",
                            ),
                        ),
                        history = listOf(WriterHistory(url = "/anime/1", lastRead = 1_600_000_000_000L)),
                        tracking = listOf(
                            WriterTracking(syncId = 2, libraryId = 7L, mediaId = 12345L, lastEpisodeSeen = 1F),
                        ),
                    ),
                ),
            ),
        )

        val anime = AniyomiBackupFormat.decode(bytes).anime.single()

        anime.source shouldBe 9L
        anime.favorite shouldBe true
        anime.viewerFlags shouldBe 42
        anime.version shouldBe 3L
        anime.backgroundUrl shouldBe "https://example/bg.jpg"
        anime.seasonNumber shouldBe 2.0
        anime.categories shouldBe listOf(0L)

        val episode = anime.episodes.single()
        episode.name shouldBe "Episode 1"
        episode.seen shouldBe true
        episode.lastSecondSeen shouldBe 1_200L
        episode.totalSeconds shouldBe 1_400L
        episode.fillermark shouldBe true
        episode.summary shouldBe "A summary"
        episode.previewUrl shouldBe "https://example/preview.jpg"

        anime.history.single().lastRead shouldBe 1_600_000_000_000L

        val track = anime.tracking.single()
        track.syncId shouldBe 2
        track.libraryId shouldBe 7L
        track.toTrack().remoteId shouldBe 12345L
    }

    /**
     * A remote id written by Tachiyomi 1.x is an Int at 3, not a Long at 100. Both are still out
     * there and a backup carries one or the other.
     */
    @Test
    fun `takes the remote id from wherever the backup put it`() {
        val bytes = encode(
            LegacyAniyomiWriter.serializer(),
            LegacyAniyomiWriter(
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                backupAnime = listOf(
                    anime(url = "/anime", title = "An anime").copy(
                        tracking = listOf(WriterTracking(syncId = 2, libraryId = 0L, mediaIdInt = 999)),
                    ),
                ),
            ),
        )

        AniyomiBackupFormat.decode(bytes).anime.single().tracking.single().toTrack().remoteId shouldBe 999L
    }

    /**
     * The premise the whole approach rests on: a field this does not know is stepped over rather
     * than thrown at. Without it there would be no way to read one half of a file at a time.
     */
    @Test
    fun `steps over fields it does not know`() {
        val bytes = encode(
            LegacyAniyomiWriter.serializer(),
            LegacyAniyomiWriter(
                backupAnime = listOf(anime(url = "/anime", title = "An anime")),
                backupAnimeSources = listOf(WriterSource(name = "AnimeSource", sourceId = 9L)),
                somethingFromTheFuture = listOf(WriterSource(name = "Unknown", sourceId = 1L)),
            ),
        )

        AniyomiBackupFormat.decode(bytes).anime.map { it.title } shouldBe listOf("An anime")
    }

    private fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        ProtoBuf.encodeToByteArray(serializer, value)

    private fun anime(url: String, title: String) = WriterAnime(source = 1L, url = url, title = title)
}

// Below: what the other apps write. Numbers copied from their sources, not from ours.

@Serializable
private data class LegacyAniyomiWriter(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<WriterAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<WriterSource> = emptyList(),
    @ProtoNumber(103) val backupAnimeSources: List<WriterSource> = emptyList(),
    @ProtoNumber(106) val backupExtensions: List<WriterExtension> = emptyList(),
    @ProtoNumber(107) val backupAnimeExtensionStores: List<WriterStore> = emptyList(),
    @ProtoNumber(108) val backupMangaExtensionRepos: List<WriterRepo> = emptyList(),
    @ProtoNumber(109) val backupCustomButtons: List<WriterButton> = emptyList(),
    @ProtoNumber(200) val somethingFromTheFuture: List<WriterSource> = emptyList(),
)

@Serializable
private data class AniyomiWriter(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<WriterSource> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionRepos: List<WriterRepo> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<WriterAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<WriterSource> = emptyList(),
    @ProtoNumber(505) val backupAnimeExtensionStores: List<WriterStore> = emptyList(),
    @ProtoNumber(506) val backupCustomButtons: List<WriterButton> = emptyList(),
)

@Serializable
private data class MihonWriter(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(106) val backupExtensionStores: List<BackupExtensionStore> = emptyList(),
)

@Serializable
private data class WriterAnime(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(16) val episodes: List<WriterEpisode> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<WriterTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(103) val viewerFlags: Int = 0,
    @ProtoNumber(104) val history: List<WriterHistory> = emptyList(),
    @ProtoNumber(109) val version: Long = 0,
    @ProtoNumber(500) val backgroundUrl: String? = null,
    @ProtoNumber(502) val parentId: Long? = null,
    @ProtoNumber(503) val id: Long? = null,
    @ProtoNumber(505) val seasonNumber: Double = -1.0,
)

@Serializable
private data class WriterEpisode(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val seen: Boolean = false,
    @ProtoNumber(6) val lastSecondSeen: Long = 0,
    @ProtoNumber(16) val totalSeconds: Long = 0,
    @ProtoNumber(9) val episodeNumber: Float = 0F,
    @ProtoNumber(501) val fillermark: Boolean = false,
    @ProtoNumber(502) val summary: String? = null,
    @ProtoNumber(503) val previewUrl: String? = null,
)

@Serializable
private data class WriterHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
)

@Serializable
private data class WriterTracking(
    @ProtoNumber(1) val syncId: Int,
    @ProtoNumber(2) val libraryId: Long,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(6) val lastEpisodeSeen: Float = 0F,
    @ProtoNumber(100) val mediaId: Long = 0,
)

@Serializable
private data class WriterSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)

@Serializable
private data class WriterStore(
    @ProtoNumber(1) val indexUrl: String,
    @ProtoNumber(2) val name: String = "Store",
    @ProtoNumber(5) val signingKey: String = "key",
    @ProtoNumber(4) val contactWebsite: String = "https://example",
)

@Serializable
private data class WriterRepo(
    @ProtoNumber(1) val baseUrl: String,
    @ProtoNumber(2) val name: String = "Repo",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "https://example",
    @ProtoNumber(5) val signingKeyFingerprint: String = "key",
)

@Serializable
private data class WriterButton(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(4) val content: String = "",
    @ProtoNumber(5) val longPressContent: String = "",
    @ProtoNumber(6) val onStartup: String = "",
)

@Serializable
private data class WriterExtension(
    @ProtoNumber(1) val pkgName: String,
    @ProtoNumber(2) val apk: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = pkgName.hashCode()
}
