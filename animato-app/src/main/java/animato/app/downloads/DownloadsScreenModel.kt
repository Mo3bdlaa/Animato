package animato.app.downloads

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Which of the three sections a queued item belongs in. */
enum class DownloadSection {
    ACTIVE,
    QUEUED,
    FAILED,
}

/** One item in the queue, whichever half put it there. */
@Immutable
data class DownloadRow(
    val key: String,
    val contentType: ContentType,
    val title: String,
    val itemName: String,
    val section: DownloadSection,
    val progress: Int,
    val coverData: Any?,
)

/**
 * How much is on disk.
 *
 * Counts come from the download caches, which hold them in memory. [bytes] does not — it is a walk
 * of the download directories, which is why it arrives after the rest of the screen rather than
 * with it.
 */
@Immutable
data class StorageSummary(
    val bytes: Long? = null,
    val items: Int = 0,
)

@Immutable
data class DownloadsState(
    val isLoading: Boolean = true,
    val lens: ContentFilter = ContentFilter.ALL,
    val storage: StorageSummary = StorageSummary(),
    val rows: List<DownloadRow> = emptyList(),
    val isRunning: Boolean = false,
    val isCleaning: Boolean = false,
) {
    val active: List<DownloadRow> get() = rows.filter { it.section == DownloadSection.ACTIVE }
    val queued: List<DownloadRow> get() = rows.filter { it.section == DownloadSection.QUEUED }
    val failed: List<DownloadRow> get() = rows.filter { it.section == DownloadSection.FAILED }
}

/**
 * One queue, both halves.
 *
 * Downloads was whichever half the lens pointed at, which is the wrong shape for a queue more than
 * for any other screen: a queue is a claim about what the device is doing *now*, and two of them
 * cannot both be that. Pausing had to be done twice, and an anime download stalling was invisible
 * from the manga queue.
 *
 * ## Why rows are rebuilt rather than mapped from a flow
 *
 * Progress and status are per-download flows, not fields on the queue, so nothing re-emits the
 * queue when a download ticks. Both signals are collected and each one rebuilds the whole list from
 * the two queues' current values. The list is short — a queue nobody is watching is a queue that
 * has finished — so rebuilding it is cheaper than maintaining a per-row subscription.
 */
class DownloadsScreenModel(
    contentPreferences: ContentPreferences = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val sweeper: OrphanedDownloadSweeper = OrphanedDownloadSweeper(),
) : ViewModel() {

    val state: StateFlow<DownloadsState>
        field = MutableStateFlow(DownloadsState(lens = contentPreferences.contentFilter.get()))

    init {
        combine(
            downloadManager.queueState,
            animeDownloadManager.queueState,
            contentPreferences.contentFilter.changes(),
        ) { manga, anime, lens -> Triple(manga, anime, lens) }
            .onEach { (_, _, lens) ->
                state.value = state.value.copy(isLoading = false, lens = lens, rows = buildRows(lens))
            }
            .launchIn(viewModelScope)

        // A download ticking does not re-emit the queue it is in, so both signals are listened to
        // separately and each one rebuilds the list.
        merge(
            downloadManager.statusFlow(),
            downloadManager.progressFlow(),
        )
            .onEach { state.value = state.value.copy(rows = buildRows(state.value.lens)) }
            .launchIn(viewModelScope)

        merge(
            animeDownloadManager.statusFlow(),
            animeDownloadManager.progressFlow(),
        )
            .onEach { state.value = state.value.copy(rows = buildRows(state.value.lens)) }
            .launchIn(viewModelScope)

        combine(
            downloadManager.isDownloaderRunning,
            animeDownloadManager.isDownloaderRunning,
        ) { manga, anime -> manga || anime }
            .onEach { running -> state.value = state.value.copy(isRunning = running) }
            .launchIn(viewModelScope)

        refreshStorage()
    }

    private fun buildRows(lens: ContentFilter): List<DownloadRow> = buildList {
        if (lens.includesManga) {
            downloadManager.queueState.value.forEach { add(it.toRow()) }
        }
        if (lens.includesAnime) {
            animeDownloadManager.queueState.value.forEach { add(it.toRow()) }
        }
    }

    /**
     * The size of both download trees.
     *
     * Recomputed rather than cached because the only thing that makes it wrong is a download
     * finishing, which is exactly when the screen is open. The anime half already has a total in
     * its cache; the manga half has none, so its source directories are walked the same way the
     * orphan sweep walks them.
     */
    fun refreshStorage() {
        viewModelScope.launchIO {
            // Walking the download tree touches storage that can be unmounted, revoked or simply
            // gone. This runs from `init`, so an unreachable root meant the Downloads screen
            // crashed on opening rather than showing a size it could not work out.
            runCatching {
                val bytes = withIOContext {
                    animeDownloadManager.getDownloadSize() + mangaDownloadBytes()
                }
                val items = downloadManager.getDownloadCount() + animeDownloadManager.getDownloadCount()
                state.value = state.value.copy(storage = StorageSummary(bytes = bytes, items = items))
            }.onFailure {
                logcat(LogPriority.WARN, it) { "Could not measure the downloads" }
            }
        }
    }

    private fun mangaDownloadBytes(): Long = sourceManager.getAll().sumOf { source ->
        downloadProvider.findSourceDir(source)?.recursiveSize() ?: 0L
    }

    fun pauseOrResume() {
        if (state.value.isRunning) {
            downloadManager.pauseDownloads()
            animeDownloadManager.pauseDownloads()
        } else {
            downloadManager.startDownloads()
            animeDownloadManager.startDownloads()
        }
    }

    fun clearQueue() {
        downloadManager.clearQueue()
        animeDownloadManager.clearQueue()
    }

    /**
     * A failed item, tried again.
     *
     * Retrying is starting the downloader: both queues keep a failed download in place with an
     * error status, and starting re-attempts everything that is not finished. There is no
     * per-download retry in either half, and inventing one here would mean reaching into the
     * downloader's own state machine from outside it.
     */
    fun retry() {
        downloadManager.startDownloads()
        animeDownloadManager.startDownloads()
    }

    fun cancel(row: DownloadRow) {
        when (row.contentType) {
            ContentType.MANGA ->
                downloadManager.queueState.value
                    .firstOrNull { it.key() == row.key }
                    ?.let { downloadManager.cancelQueuedDownloads(listOf(it)) }
            ContentType.ANIME ->
                animeDownloadManager.queueState.value
                    .firstOrNull { it.key() == row.key }
                    ?.let { animeDownloadManager.cancelQueuedDownloads(listOf(it)) }
        }
    }

    /** Removes download folders for entries no longer in either library, then restates the size. */
    fun cleanUp(onResult: (OrphanedDownloadSweeper.Result) -> Unit) {
        state.value = state.value.copy(isCleaning = true)
        viewModelScope.launchIO {
            // The reset is in `finally`, because it was the throw that skipped it: a sweep over
            // unreachable storage left the spinner turning for as long as the screen was open,
            // with the failure delivered as a crash rather than as a stopped spinner.
            try {
                val result = sweeper.sweep()
                refreshStorage()
                onResult(result)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Cleaning up downloads failed" }
            } finally {
                state.value = state.value.copy(isCleaning = false)
            }
        }
    }

    private fun Download.key() = "manga-${chapter.id}"

    private fun AnimeDownload.key() = "anime-${episode.id}"

    private fun Download.toRow() = DownloadRow(
        key = key(),
        contentType = ContentType.MANGA,
        title = manga.title,
        itemName = chapter.name,
        section = status.toSection(),
        progress = progress,
        coverData = null,
    )

    private fun AnimeDownload.toRow() = DownloadRow(
        key = key(),
        contentType = ContentType.ANIME,
        title = anime.title,
        itemName = episode.name,
        section = status.toSection(),
        progress = progress,
        coverData = null,
    )

    private fun Download.State.toSection() = when (this) {
        Download.State.DOWNLOADING -> DownloadSection.ACTIVE
        Download.State.ERROR -> DownloadSection.FAILED
        else -> DownloadSection.QUEUED
    }

    private fun AnimeDownload.State.toSection() = when (this) {
        AnimeDownload.State.DOWNLOADING -> DownloadSection.ACTIVE
        AnimeDownload.State.ERROR -> DownloadSection.FAILED
        else -> DownloadSection.QUEUED
    }
}

/**
 * Bytes under a directory, counted by walking it.
 *
 * `UniFile.size()` answers for a file; on a directory the answer depends on which backing document
 * provider is behind it, and on a SAF tree it is zero. Walking is the only thing that is right on
 * every storage location the app can be pointed at.
 */
private fun UniFile.recursiveSize(): Long {
    if (!isDirectory) return length()
    return listFiles()?.sumOf { it.recursiveSize() } ?: 0L
}
