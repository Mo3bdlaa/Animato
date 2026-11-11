// Adapted from https://github.com/mihonapp/mihon
// Copyright The Mihon Open Source Project, Apache-2.0
package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadQueueScreenModel(
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<List<AnimeDownloadQueueSection>> = downloadManager.queueState
        .map { downloads ->
            downloads
                .groupBy { it.source }
                .map { (source, downloads) -> AnimeDownloadQueueSection(source.id, source.name, downloads) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<AnimeDownload>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancel(downloads: List<AnimeDownload>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    /**
     * Moves [download] to the top or bottom of its section.
     */
    fun move(download: AnimeDownload, toTop: Boolean) {
        reorder(
            state.value.flatMap { section ->
                if (download in section.downloads) {
                    val others = section.downloads - download
                    if (toTop) listOf(download) + others else others + download
                } else {
                    section.downloads
                }
            },
        )
    }

    /**
     * Moves all the downloads of the anime of [download] to the top or bottom of the queue.
     */
    fun moveSeries(download: AnimeDownload, toTop: Boolean) {
        val (selectedSeries, otherSeries) = state.value
            .flatMap { it.downloads }
            .partition { it.anime.id == download.anime.id }
        reorder(if (toTop) selectedSeries + otherSeries else otherSeries + selectedSeries)
    }

    fun cancelSeries(download: AnimeDownload) {
        val allDownloadsForSeries = state.value
            .flatMap { it.downloads }
            .filter { it.anime.id == download.anime.id }
        if (allDownloadsForSeries.isNotEmpty()) {
            cancel(allDownloadsForSeries)
        }
    }

    /**
     * Sorts the downloads of every section by [selector].
     */
    fun <R : Comparable<R>> reorderQueue(selector: (AnimeDownload) -> R?, reverse: Boolean = false) {
        reorder(
            state.value.flatMap { section ->
                section.downloads.sortedBy(selector).let { if (reverse) it.reversed() else it }
            },
        )
    }
}

/**
 * Downloads of the queue from one source.
 */
@Immutable
data class AnimeDownloadQueueSection(
    val id: Long,
    val name: String,
    val downloads: List<AnimeDownload>,
)
