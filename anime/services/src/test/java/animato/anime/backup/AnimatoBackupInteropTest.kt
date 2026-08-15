package animato.anime.backup

import animato.anime.backup.models.AniyomiBackupWriteEnvelope
import animato.anime.backup.models.BackupAnime
import animato.anime.backup.models.BackupAnimeSource
import animato.anime.backup.models.BackupEpisode
import animato.anime.backup.models.WritableBackupCustomButton
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * That a backup written here can be read by the two apps it claims to be readable by.
 *
 * The claim is in the settings screen, in the readme and in the commit that added it, and it is the
 * reason this app writes Aniyomi's format instead of one of its own. A claim like that is worth a
 * test, because the way it breaks is silent: the file is written, the user believes they have a
 * backup, and they find out otherwise on the day they need it.
 *
 * The readers below are declared here with the field rules the other apps actually use — no
 * defaults, so a field left out is a hard failure — rather than reusing this app's own models,
 * which have a default for everything and would pass whatever they were given.
 */
@Execution(ExecutionMode.CONCURRENT)
class AnimatoBackupInteropTest {

    private val backup = AniyomiBackupWriteEnvelope(
        backupManga = listOf(BackupManga(source = 1L, url = "/manga", title = "A manga")),
        backupCategories = listOf(BackupCategory(name = "Reading", order = 0)),
        backupMangaExtensionStores = listOf(
            BackupExtensionStore(
                indexUrl = "https://manga.example",
                name = "Manga store",
                badgeLabel = "Manga",
                signingKey = "manga-key",
                contactWebsite = "https://manga.example/about",
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        isLegacy = false,
        backupAnime = listOf(
            BackupAnime(
                source = 9L,
                url = "/anime",
                title = "An anime",
                episodes = listOf(BackupEpisode(url = "/anime/1", name = "Episode 1", seen = true)),
            ),
        ),
        backupAnimeSources = listOf(BackupAnimeSource(name = "AnimeSource", sourceId = 9L)),
        backupAnimeExtensionStores = listOf(
            BackupExtensionStore(
                indexUrl = "https://anime.example",
                name = "Anime store",
                badgeLabel = "Anime",
                signingKey = "anime-key",
                contactWebsite = "https://anime.example/about",
                contactDiscord = null,
                isLegacy = false,
                extensionListUrl = null,
            ),
        ),
        backupCustomButtons = listOf(
            WritableBackupCustomButton(
                name = "Skip intro",
                isFavorite = false,
                sortIndex = 0,
                content = "-- lua",
                longPressContent = "",
                onStartup = "",
            ),
        ),
    )

    private val bytes = ProtoBuf.encodeToByteArray(AniyomiBackupWriteEnvelope.serializer(), backup)

    @Test
    fun `reads back everything it wrote`() {
        val decoded = AniyomiBackupFormat.decode(bytes)

        decoded.anime.map { it.title } shouldBe listOf("An anime")
        decoded.anime.single().episodes.single().seen shouldBe true
        decoded.animeSources.map { it.sourceId } shouldBe listOf(9L)
        decoded.animeExtensionStores.map { it.indexUrl } shouldBe listOf("https://anime.example")
        decoded.customButtons.map { it.name } shouldBe listOf("Skip intro")
        decoded.manga.map { it.title } shouldBe listOf("A manga")
        decoded.mangaCategories.map { it.name } shouldBe listOf("Reading")
        decoded.mangaExtensionStores.map { it.baseUrl } shouldBe listOf("https://manga.example")
    }

    /**
     * Mihon's own model, not a copy of it — the real class, the real strictness.
     */
    @Test
    fun `Mihon reads the manga in it`() {
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        decoded.backupManga.map { it.title } shouldBe listOf("A manga")
        decoded.backupCategories.map { it.name } shouldBe listOf("Reading")
        decoded.backupExtensionStores.map { it.indexUrl } shouldBe listOf("https://manga.example")
        decoded.backupExtensionStores.map { it.signingKey } shouldBe listOf("manga-key")
    }

    /**
     * Aniyomi reads all of it, including the three models whose fields it treats as mandatory.
     *
     * Those three are the whole reason `AniyomiBackupWriteEnvelope` exists. Written from the model
     * this app reads into, an extension store or a custom button holding a default value would go
     * out with that field left off, and this decode would throw rather than fail an assertion.
     */
    @Test
    fun `Aniyomi reads all of it`() {
        val decoded = ProtoBuf.decodeFromByteArray(AniyomiReader.serializer(), bytes)

        decoded.isLegacy shouldBe false
        decoded.backupAnime.map { it.title } shouldBe listOf("An anime")
        decoded.backupAnime.single().episodes.single().name shouldBe "Episode 1"
        decoded.backupManga.map { it.title } shouldBe listOf("A manga")
        decoded.backupAnimeExtensionStores.map { it.indexUrl } shouldBe listOf("https://anime.example")
        decoded.backupAnimeExtensionStores.map { it.signingKey } shouldBe listOf("anime-key")
        decoded.backupMangaExtensionRepos.map { it.baseUrl } shouldBe listOf("https://manga.example")
        decoded.backupCustomButtons.map { it.name } shouldBe listOf("Skip intro")
    }
}

// Aniyomi's models, with its field rules: the store and button fields have no defaults, so leaving
// one out is a decoding failure rather than something quietly filled in.

@Serializable
private data class AniyomiReader(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionRepos: List<AniyomiRepo> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<AniyomiAnime> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<AniyomiSource> = emptyList(),
    @ProtoNumber(505) val backupAnimeExtensionStores: List<AniyomiStore> = emptyList(),
    @ProtoNumber(506) val backupCustomButtons: List<AniyomiButton> = emptyList(),
)

@Serializable
private data class AniyomiAnime(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(16) val episodes: List<AniyomiEpisode> = emptyList(),
)

@Serializable
private data class AniyomiEpisode(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val seen: Boolean = false,
)

@Serializable
private data class AniyomiSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)

@Serializable
private data class AniyomiStore(
    @ProtoNumber(1) val indexUrl: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val badgeLabel: String?,
    @ProtoNumber(5) val signingKey: String,
    @ProtoNumber(4) val contactWebsite: String,
    @ProtoNumber(6) val contactDiscord: String?,
    @ProtoNumber(7) val isLegacy: Boolean?,
    @ProtoNumber(8) val extensionListUrl: String?,
)

@Serializable
private data class AniyomiRepo(
    @ProtoNumber(1) val baseUrl: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val shortName: String?,
    @ProtoNumber(4) val website: String,
    @ProtoNumber(5) val signingKeyFingerprint: String,
)

@Serializable
private data class AniyomiButton(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val isFavorite: Boolean,
    @ProtoNumber(3) val sortIndex: Long,
    @ProtoNumber(4) val content: String,
    @ProtoNumber(5) val longPressContent: String,
    @ProtoNumber(6) val onStartup: String,
)
