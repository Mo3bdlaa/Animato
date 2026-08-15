package animato.anime.backup.models

import animato.domain.category.AnimeCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * An Aniyomi backup, once the layout it arrived in stops mattering.
 *
 * Both halves are here. The manga half is Mihon's own models, unchanged, because Aniyomi never
 * altered them — a backup's manga, chapters, history and tracks are byte-for-byte what Mihon
 * writes, so they go straight into Mihon's own restorers.
 */
data class AniyomiBackup(
    val anime: List<BackupAnime> = emptyList(),
    val animeCategories: List<BackupCategory> = emptyList(),
    val animeSources: List<BackupAnimeSource> = emptyList(),
    val animeExtensionStores: List<BackupAnimeExtensionStore> = emptyList(),
    val customButtons: List<BackupCustomButton> = emptyList(),

    val manga: List<BackupManga> = emptyList(),
    val mangaCategories: List<BackupCategory> = emptyList(),
    val mangaSources: List<BackupSource> = emptyList(),
    val mangaExtensionStores: List<BackupMangaExtensionStore> = emptyList(),

    val preferences: List<BackupPreference> = emptyList(),
    val sourcePreferences: List<BackupSourcePreferences> = emptyList(),
) {

    val isEmpty: Boolean
        get() = anime.isEmpty() &&
            animeCategories.isEmpty() &&
            animeExtensionStores.isEmpty() &&
            customButtons.isEmpty() &&
            manga.isEmpty() &&
            mangaCategories.isEmpty() &&
            mangaExtensionStores.isEmpty() &&
            preferences.isEmpty() &&
            sourcePreferences.isEmpty()
}

/**
 * The layout Aniyomi writes now.
 *
 * Anime was moved out to 500 and up so that a fork claiming the low numbers could no longer
 * collide with it. Everything below 500 is Mihon's numbering and means what Mihon means by it.
 */
@Serializable
internal data class AniyomiBackupEnvelope(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    // 100 held a source model whose proto numbering was invalid; 101 replaced it.
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionStores: List<BackupMangaExtensionStore> = emptyList(),

    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    // 504 holds extensions — the apk files themselves. Installing an apk out of a backup is not
    // something this importer does, so the number is noted and skipped.
    @ProtoNumber(505) val backupAnimeExtensionStores: List<BackupAnimeExtensionStore> = emptyList(),
    @ProtoNumber(506) val backupCustomButtons: List<BackupCustomButton> = emptyList(),
) {

    fun toAniyomiBackup() = AniyomiBackup(
        anime = backupAnime,
        animeCategories = backupAnimeCategories,
        animeSources = backupAnimeSources,
        animeExtensionStores = backupAnimeExtensionStores,
        customButtons = backupCustomButtons,
        manga = backupManga,
        mangaCategories = backupCategories,
        mangaSources = backupSources,
        mangaExtensionStores = backupMangaExtensionStores,
        preferences = backupPreferences,
        sourcePreferences = backupSourcePreferences,
    )
}

/**
 * The layout every released Aniyomi wrote, and so the one that actually turns up.
 *
 * Anime sat at 3 and 4, immediately beside manga at 1 and 2, and its source list at 103 beside
 * manga's at 101. That worked only while no other fork claimed those numbers.
 *
 * 106 is the reason this importer reads the file itself instead of handing it to Mihon. Here it
 * holds extension apks; in a Mihon backup it holds extension stores. The two are different shapes
 * with the same number, and Mihon's decoder rejects the whole file when it meets one — so a user
 * with extensions in their Aniyomi backup could not restore even the manga half through Mihon.
 * Manga repositories are at 108 in this layout, which is where this reads them from.
 */
@Serializable
internal data class LegacyAniyomiBackupEnvelope(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(103) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(107) val backupAnimeExtensionStores: List<BackupAnimeExtensionStore> = emptyList(),
    @ProtoNumber(108) val backupMangaExtensionStores: List<BackupMangaExtensionStore> = emptyList(),
    @ProtoNumber(109) val backupCustomButtons: List<BackupCustomButton> = emptyList(),
) {

    fun toAniyomiBackup() = AniyomiBackup(
        anime = backupAnime,
        animeCategories = backupAnimeCategories,
        animeSources = backupAnimeSources,
        animeExtensionStores = backupAnimeExtensionStores,
        customButtons = backupCustomButtons,
        manga = backupManga,
        mangaCategories = backupCategories,
        mangaSources = backupSources,
        mangaExtensionStores = backupMangaExtensionStores,
        preferences = backupPreferences,
        sourcePreferences = backupSourcePreferences,
    )
}

/**
 * Just enough of a backup to tell the two layouts apart.
 *
 * Aniyomi stamps 500 to say which one it wrote, and leaves it out of the old layout — but a Mihon
 * backup has no 500 either, and reading one as the old layout would look for manga repositories at
 * 108 and quietly find none. So the stamp alone is not enough: the file also has to actually carry
 * anime at the old numbers.
 *
 * Decoding into a message with no fields is how the anime entries get counted without being built.
 */
@Serializable
internal data class BackupLayoutProbe(
    @ProtoNumber(3) val legacyAnime: List<Skipped> = emptyList(),
    @ProtoNumber(103) val legacyAnimeSources: List<Skipped> = emptyList(),
    @ProtoNumber(500) val isLegacy: Boolean = true,
) {

    val isLegacyLayout: Boolean
        get() = isLegacy && (legacyAnime.isNotEmpty() || legacyAnimeSources.isNotEmpty())

    @Serializable
    internal class Skipped
}

/**
 * The anime category this backup category describes, under an id the anime table has given it.
 *
 * Mihon's own `toCategory` is right there and returns the wrong type: its categories live in a
 * different table with a different id space, and ours carries a field its own does not.
 */
fun BackupCategory.toAnimeCategory(id: Long) = AnimeCategory(
    id = id,
    name = name,
    flags = flags,
    order = order,
    hidden = false,
)
