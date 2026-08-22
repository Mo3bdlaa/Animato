package animato.anime.backup.restore

import android.content.Context
import android.net.Uri
import animato.anime.backup.AniyomiBackupDecoder
import animato.anime.backup.create.AnimatoBackupCreateJob
import animato.anime.backup.models.AniyomiBackup
import animato.anime.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Restores an Aniyomi backup — both halves of it.
 *
 * The manga half is not re-implemented. Mihon's own restorers take the models straight out of the
 * file, because Aniyomi never changed them. What is here instead of Mihon's [BackupRestorer][
 * eu.kanade.tachiyomi.data.backup.restore.BackupRestorer] is the part that has to differ: the
 * decoding, which knows two field layouts Mihon does not, and an order of work that includes the
 * anime library.
 *
 * Restoring is safe to repeat. Nothing is deleted, entries already present are merged, and
 * progress is taken as the furthest of the two sides.
 */
class AniyomiBackupRestorer(
    private val context: Context,
    private val notifier: BackupNotifier,

    private val animeRestorer: AnimeRestorer = AnimeRestorer(),
    private val animeCategoriesRestorer: AnimeCategoriesRestorer = AnimeCategoriesRestorer(),
    private val animeExtensionStoreRestorer: AnimeExtensionStoreRestorer = AnimeExtensionStoreRestorer(),
    private val customButtonRestorer: CustomButtonRestorer = CustomButtonRestorer(),

    private val mangaRestorer: MangaRestorer = MangaRestorer(),
    private val mangaCategoriesRestorer: CategoriesRestorer = CategoriesRestorer(),
    private val mangaExtensionStoreRestorer: ExtensionStoreRestorer = ExtensionStoreRestorer(),
    private val preferenceRestorer: PreferenceRestorer = PreferenceRestorer(context),
) {

    private var restoreAmount = 0
    private val restoreProgress = AtomicInteger(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()

    private var animeSourceNames: Map<Long, String> = emptyMap()
    private var mangaSourceNames: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        if (options.libraryEntries) {
            // Downloads on disk were never touched, but nothing in the library knew about them a
            // moment ago. Without this the restored entries all read as undownloaded.
            invalidateDownloadCaches()
        }

        val logFile = writeErrorLog()
        notifier.showRestoreComplete(
            System.currentTimeMillis() - startTime,
            errors.size,
            logFile.parent,
            logFile.name,
            false,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val backup = AniyomiBackupDecoder(context).decode(uri)

        animeSourceNames = backup.animeSources.associate { it.sourceId to it.name }
        mangaSourceNames = backup.mangaSources.associate { it.sourceId to it.name }

        val (parents, seasons) = backup.anime.partition { it.parentId == null }
        val seasonsByParent = seasons.groupBy { it.parentId }

        if (options.libraryEntries) {
            restoreAmount += parents.size + backup.manga.size
        }
        if (options.categories) {
            restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.animeExtensionStores.size + backup.mangaExtensionStores.size
        }

        /*
         * Categories first, and finished, before anything is filed into them.
         *
         * These all used to start at once inside one `coroutineScope`. Two things followed from
         * that, both silent. An entry restored before the category insert had committed looked its
         * category up by name, did not find it, and was filed nowhere — restore reported success
         * and the shelves came back empty. And because `coroutineScope` cancels siblings, a throw
         * in any step killed the library restore *mid-write*: a partial library, and the collected
         * per-entry errors discarded on the way out, so the one thing that would have said which
         * entries were lost never got written.
         *
         * The steps that can be concurrent still are — preferences and stores touch nothing the
         * library restore touches. They are supervised so that a preference blob from a different
         * app version, which is exactly the input this path exists for, cannot abort a library that
         * is halfway restored.
         */
        if (options.categories) {
            coroutineScope { restoreCategories(backup) }
        }

        if (options.libraryEntries) {
            coroutineScope {
                restoreAnime(
                    parents = parents,
                    seasonsByParent = seasonsByParent,
                    categories = if (options.categories) backup.animeCategories else emptyList(),
                )
                restoreManga(backup, if (options.categories) backup.mangaCategories else emptyList())
            }
        }

        supervisorScope {
            if (options.appSettings) {
                restoreAppPreferences(backup, options)
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup)
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup)
            }
        }
    }

    private fun CoroutineScope.restoreCategories(backup: AniyomiBackup) = launch {
        ensureActive()
        animeCategoriesRestorer(backup.animeCategories)
        mangaCategoriesRestorer(backup.mangaCategories)
        reportProgress(context.stringResource(MR.strings.categories))
    }

    /**
     * Restores each top-level anime together with its seasons.
     *
     * Seasons are not restored on their own. A season points at its parent by an id that belongs to
     * the backup, so restoring one outside its parent would either lose the link or attach it to
     * whichever row happens to hold that number here. A season whose parent is not in the backup is
     * restored as an entry in its own right, which is what it now is.
     */
    private fun CoroutineScope.restoreAnime(
        parents: List<BackupAnime>,
        seasonsByParent: Map<Long?, List<BackupAnime>>,
        categories: List<BackupCategory>,
    ) = launch {
        val known = parents.mapNotNull { it.id }.toSet()
        val orphans = seasonsByParent.filterKeys { it !in known }.values.flatten()

        animeRestorer.sortByNew(parents + orphans).forEach { anime ->
            ensureActive()

            try {
                animeRestorer.restore(
                    backupAnime = anime,
                    backupCategories = categories,
                    seasons = anime.id?.let { seasonsByParent[it] }.orEmpty(),
                )
            } catch (e: Exception) {
                ensureActive()
                val sourceName = animeSourceNames[anime.source] ?: anime.source.toString()
                errors.add(Date() to "${anime.title} [$sourceName]: ${e.message}")
            }

            reportProgress(anime.title)
        }
    }

    private fun CoroutineScope.restoreManga(
        backup: AniyomiBackup,
        categories: List<BackupCategory>,
    ) = launch {
        mangaRestorer.sortByNew(backup.manga).forEach { manga ->
            ensureActive()

            try {
                mangaRestorer.restore(manga, categories)
            } catch (e: Exception) {
                ensureActive()
                val sourceName = mangaSourceNames[manga.source] ?: manga.source.toString()
                errors.add(Date() to "${manga.title} [$sourceName]: ${e.message}")
            }

            reportProgress(manga.title)
        }
    }

    private fun CoroutineScope.restoreAppPreferences(
        backup: AniyomiBackup,
        options: RestoreOptions,
    ) = launch {
        ensureActive()
        preferenceRestorer.restoreApp(
            backup.preferences,
            backup.mangaCategories.takeIf { options.categories },
        )
        // The player's custom buttons ride with the settings. They are settings, they are small,
        // and giving them a checkbox of their own would mean explaining what they are twice.
        customButtonRestorer(backup.customButtons)

        // Mihon's preference restorer ends by scheduling Mihon's backup job from the interval it
        // just restored. That job writes manga only, so the slot is claimed back here, at the
        // restored interval, before anything can fire.
        AnimatoBackupCreateJob.setupTask(context)

        reportProgress(context.stringResource(MR.strings.app_settings))
    }

    private fun CoroutineScope.restoreSourcePreferences(backup: AniyomiBackup) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(backup.sourcePreferences)
        reportProgress(context.stringResource(MR.strings.source_settings))
    }

    private fun CoroutineScope.restoreExtensionStores(backup: AniyomiBackup) = launch {
        backup.animeExtensionStores.forEach { store ->
            ensureActive()
            try {
                animeExtensionStoreRestorer(store)
            } catch (e: Exception) {
                ensureActive()
                errors.add(Date() to "${store.name}: ${e.message}")
            }
            reportProgress(store.name)
        }

        backup.mangaExtensionStores.filter { it.baseUrl.isNotBlank() }.forEach { store ->
            ensureActive()
            try {
                mangaExtensionStoreRestorer(store.toBackupExtensionStore())
            } catch (e: Exception) {
                ensureActive()
                errors.add(Date() to "${store.name}: ${e.message}")
            }
            reportProgress(store.name)
        }
    }

    private fun invalidateDownloadCaches() {
        listOf<() -> Unit>(
            { Injekt.get<AnimeDownloadCache>().invalidateCache() },
            { Injekt.get<DownloadCache>().invalidateCache() },
        ).forEach { invalidate ->
            try {
                invalidate()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate a download cache after restore" }
            }
        }
    }

    private fun reportProgress(content: String) {
        notifier.showRestoreProgress(content, restoreProgress.incrementAndGet(), restoreAmount, false)
    }

    private fun writeErrorLog(): File {
        if (errors.isEmpty()) return File("")
        return try {
            val file = context.createFileInCacheDir(ERROR_LOG_NAME)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            file.bufferedWriter().use { out ->
                out.write(context.stringResource(AYMR.strings.aniyomi_import_error_log_header))
                out.write("\n\n")
                errors.forEach { (date, message) ->
                    out.write("[${format.format(date)}] $message\n")
                }
            }
            file
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write the restore error log" }
            File("")
        }
    }

    private companion object {
        const val ERROR_LOG_NAME = "animato_restore_error.txt"
    }
}
