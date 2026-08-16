package animato.app.downloads

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Deletes downloaded files belonging to entries that are no longer in the library.
 *
 * Removing something from the library leaves its episodes and chapters on disk forever. Neither
 * half of this app had anything that cleaned them up, and neither does Mihon: its download
 * preferences cover *read* chapters and bookmarks, never *removed entries*. The result is gigabytes
 * that nothing in the app will ever mention again, and the most common way people notice is running
 * out of storage.
 *
 * ## Why a sweep rather than a hook
 *
 * The obvious design is to delete at the moment something leaves the library. That is a Mihon file
 * for the manga half, which we do not edit — but it is also the weaker design. An entry can leave
 * the library through a long-press menu, a mass deselect, a backup restore, or a category being
 * emptied, and a hook has to be attached to every one of them. A sweep asks the question that
 * actually matters, which is not *"was something just removed"* but *"is anything on disk unclaimed
 * right now"*.
 *
 * ## What it deletes, exactly
 *
 * For every **installed** source, the entry folders inside that source's download folder whose name
 * does not match a favourite of that source. Names are compared as the download provider builds
 * them — `getMangaDirName(title)` on both sides of the comparison, never a name parsed back out of a
 * path.
 *
 * Two deliberate limits, both of which fail towards keeping files rather than deleting them:
 *
 * - **Uninstalled sources are skipped entirely.** Their folders cannot be attributed to a source, so
 *   the favourites of that source cannot be worked out, so nothing there is safe to judge.
 * - **Nothing runs while a downloader is running**, because deleting a folder underneath an
 *   in-progress download is a corrupt file rather than a reclaimed one.
 *
 * ## The one case where this deletes something wanted
 *
 * Downloads are stored under the entry's **title**, so if a source renames an entry, the folder
 * keeps the old name and matches no favourite. This sweep deletes it.
 *
 * That is worth stating plainly rather than hiding, and it is not a new blind spot: Mihon's
 * `DownloadCache` also looks entry folders up by current title, so those files already showed as
 * *not downloaded* everywhere in the app. The change is that they now go away instead of sitting
 * there invisibly — which is the whole point of the feature, and still a surprise if it happens to
 * you.
 */
class OrphanedDownloadSweeper(
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaProvider: DownloadProvider = Injekt.get(),
    private val animeProvider: AnimeDownloadProvider = Injekt.get(),
    private val mangaCache: DownloadCache = Injekt.get(),
    private val animeCache: AnimeDownloadCache = Injekt.get(),
    private val mangaDownloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
) {

    /**
     * How many entry folders were removed, per half.
     *
     * Returned rather than logged so a settings screen can say what happened. A cleanup that
     * silently reports nothing is indistinguishable from one that did not run — which is a mistake
     * this project has already made once, with the update checker.
     */
    data class Result(val manga: Int, val anime: Int) {
        val total: Int get() = manga + anime
    }

    suspend fun sweep(): Result = withIOContext {
        if (mangaDownloadManager.isRunning || animeDownloadManager.isRunning) {
            logcat(LogPriority.DEBUG) { "Skipping orphaned download sweep: a downloader is running" }
            return@withIOContext Result(manga = 0, anime = 0)
        }

        val manga = sweepManga()
        val anime = sweepAnime()

        // Only when something changed. The cache rebuild walks the whole downloads tree, which is
        // the expensive part of this and pointless when nothing moved.
        if (manga > 0) mangaCache.invalidateCache()
        if (anime > 0) animeCache.invalidateCache()

        Result(manga = manga, anime = anime)
    }

    private suspend fun sweepManga(): Int {
        val keepBySource = mangaRepository.getFavorites()
            .groupBy(
                keySelector = { it.source },
                valueTransform = { mangaProvider.getMangaDirName(it.title) },
            )
            .mapValues { (_, names) -> names.toSet() }

        return sourceManager.getAll().sumOf { source ->
            val sourceDir = mangaProvider.findSourceDir(source) ?: return@sumOf 0
            deleteUnclaimed(sourceDir, keepBySource[source.id].orEmpty())
        }
    }

    private suspend fun sweepAnime(): Int {
        val keepBySource = animeRepository.getAnimeFavorites()
            .groupBy(
                keySelector = { it.source },
                valueTransform = { animeProvider.getAnimeDirName(it.title) },
            )
            .mapValues { (_, names) -> names.toSet() }

        return animeSourceManager.getAll().sumOf { source ->
            val sourceDir = animeProvider.findSourceDir(source) ?: return@sumOf 0
            deleteUnclaimed(sourceDir, keepBySource[source.id].orEmpty())
        }
    }

    /**
     * Removes every directory in [sourceDir] whose name is not in [keep].
     *
     * Files sitting directly in a source folder are left alone. Nothing this app writes puts one
     * there, so anything that is one was put there by something else, and guessing about it is how
     * a cleanup becomes a data loss report.
     */
    private fun deleteUnclaimed(sourceDir: UniFile, keep: Set<String>): Int {
        val entries = sourceDir.listFiles() ?: return 0
        var deleted = 0

        entries.forEach { entry ->
            if (!entry.isDirectory) return@forEach
            val name = entry.name ?: return@forEach
            if (name in keep) return@forEach

            if (entry.delete()) {
                deleted++
            } else {
                logcat(LogPriority.WARN) { "Could not delete orphaned download folder: $name" }
            }
        }

        return deleted
    }
}
