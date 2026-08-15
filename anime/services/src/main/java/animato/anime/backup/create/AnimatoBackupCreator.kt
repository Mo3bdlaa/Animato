package animato.anime.backup.create

import android.content.Context
import android.net.Uri
import animato.anime.backup.AniyomiBackupValidator
import animato.anime.backup.models.AniyomiBackupWriteEnvelope
import animato.anime.backup.models.toWritable
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a backup with both libraries in it.
 *
 * The file is an Aniyomi backup, in the layout Aniyomi writes now — not a format of our own. That
 * costs nothing and it means the file is readable by three apps rather than one: Animato reads all
 * of it, Aniyomi reads all of it, and Mihon reads the manga half. Nobody is locked in by having
 * chosen this app.
 *
 * The manga half is built by Mihon's own creators, called and not copied. Only the anime creators
 * and the envelope they go into are ours.
 */
class AnimatoBackupCreator(
    private val context: Context,
    private val isAutoBackup: Boolean,

    private val parser: ProtoBuf = Injekt.get(),
    private val backupPreferences: BackupPreferences = Injekt.get(),

    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get(),
    private val getSeasons: GetAnimeSeasonsByParentId = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val animeBackupCreator: AnimeBackupCreator = AnimeBackupCreator(),
    private val animeCategoriesBackupCreator: AnimeCategoriesBackupCreator = AnimeCategoriesBackupCreator(),
    private val animeSourcesBackupCreator: AnimeSourcesBackupCreator = AnimeSourcesBackupCreator(),
    private val animeExtensionStoresBackupCreator: AnimeExtensionStoresBackupCreator =
        AnimeExtensionStoresBackupCreator(),
    private val customButtonBackupCreator: CustomButtonBackupCreator = CustomButtonBackupCreator(),

    private val getMangaFavorites: GetFavorites = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val mangaBackupCreator: MangaBackupCreator = MangaBackupCreator(),
    private val mangaCategoriesBackupCreator: CategoriesBackupCreator = CategoriesBackupCreator(),
    private val mangaSourcesBackupCreator: SourcesBackupCreator = SourcesBackupCreator(),
    private val mangaExtensionStoresBackupCreator: ExtensionStoresBackupCreator = ExtensionStoresBackupCreator(),
    private val preferenceBackupCreator: PreferenceBackupCreator = PreferenceBackupCreator(),
) {

    /**
     * Writes the backup and returns where it went.
     */
    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = openFile(uri)
                ?: throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))

            val backupAnime = if (options.libraryEntries) {
                animeBackupCreator(collectAnime(options.readEntries), options)
            } else {
                emptyList()
            }

            val nonLibraryManga = if (options.readEntries && options.libraryEntries) {
                mangaRepository.getReadMangaNotInLibrary()
            } else {
                emptyList()
            }
            val backupManga = if (options.libraryEntries) {
                mangaBackupCreator(getMangaFavorites.await() + nonLibraryManga, options)
            } else {
                emptyList()
            }

            val envelope = AniyomiBackupWriteEnvelope(
                backupManga = backupManga,
                backupCategories = if (options.categories) mangaCategoriesBackupCreator() else emptyList(),
                backupSources = mangaSourcesBackupCreator(backupManga),
                backupPreferences = if (options.appSettings) {
                    preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
                } else {
                    emptyList()
                },
                backupSourcePreferences = if (options.sourceSettings) {
                    preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
                } else {
                    emptyList()
                },
                backupMangaExtensionStores = if (options.extensionStores) {
                    mangaExtensionStoresBackupCreator()
                } else {
                    emptyList()
                },

                // Says which of the two layouts this is. Without it, an app reading this file would
                // look for anime at the numbers the old layout used and find none.
                isLegacy = false,
                backupAnime = backupAnime,
                backupAnimeCategories = if (options.categories) animeCategoriesBackupCreator() else emptyList(),
                backupAnimeSources = animeSourcesBackupCreator(backupAnime),
                backupAnimeExtensionStores = if (options.extensionStores) {
                    animeExtensionStoresBackupCreator().map { it.toWritable() }
                } else {
                    emptyList()
                },
                backupCustomButtons = if (options.appSettings) {
                    customButtonBackupCreator().map { it.toWritable() }
                } else {
                    emptyList()
                },
            )

            val bytes = parser.encodeToByteArray(AniyomiBackupWriteEnvelope.serializer(), envelope)
            if (bytes.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            file.openOutputStream()
                // Truncate first: writing a shorter backup over a longer one otherwise leaves the
                // tail of the old file behind, and the result is not a backup at all.
                .also { (it as? FileOutputStream)?.channel?.truncate(0) }
                .sink().gzip().buffer()
                .use { it.write(bytes) }

            // Read it back before calling it a backup. A file that cannot be decoded is worse than
            // no file, because the user believes they have one.
            AniyomiBackupValidator(context).validate(file.uri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(System.currentTimeMillis())
            }

            return file.uri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    /**
     * Every anime worth writing down: the library, its seasons, and — if asked — anything watched
     * outside the library.
     *
     * Seasons are collected explicitly rather than left to the watched sweep. A season is usually
     * not a favourite in its own right, so without this a user's season progress would only be in
     * the backup if they happened to have watched some of it and happened to leave that option on.
     */
    private suspend fun collectAnime(includeWatchedOutsideLibrary: Boolean): List<Anime> {
        val favorites = getAnimeFavorites.await()
        val seasons = favorites.flatMap { getSeasons.await(it.id).map { season -> season.anime } }
        val watched = if (includeWatchedOutsideLibrary) animeRepository.getWatchedAnimeNotInLibrary() else emptyList()

        return (favorites + seasons + watched).distinctBy { it.id }
    }

    private fun openFile(uri: Uri): UniFile? {
        if (!isAutoBackup) return UniFile.fromUri(context, uri)?.takeIf { it.isFile }

        val dir = UniFile.fromUri(context, uri) ?: return null
        dir.listFiles { _, name -> filenameRegex.matches(name) }
            .orEmpty()
            .sortedByDescending { it.name }
            .drop(MAX_AUTO_BACKUPS - 1)
            .forEach { it.delete() }

        return dir.createFile(filename(context))?.takeIf { it.isFile }
    }

    private val filenameRegex
        get() = """${Regex.escape(context.packageName)}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}\.tachibk""".toRegex()

    companion object {
        private const val MAX_AUTO_BACKUPS = 4

        /**
         * What a backup is called. The name carries the date so that four of them sort by age.
         */
        fun filename(context: Context): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${context.packageName}_$date.tachibk"
        }
    }
}
