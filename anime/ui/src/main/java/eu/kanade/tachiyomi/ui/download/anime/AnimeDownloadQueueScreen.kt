package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeDownloadQueueScreen(
    contentPadding: PaddingValues,
    screenModel: AnimeDownloadQueueScreenModel,
    downloadList: List<AnimeDownloadQueueSection>,
    nestedScrollConnection: NestedScrollConnection,
) {
    if (downloadList.isEmpty()) {
        EmptyScreen(
            stringRes = MR.strings.information_no_downloads,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    AnimeDownloadQueueList(
        sections = downloadList,
        lazyListState = rememberLazyListState(),
        contentPadding = contentPadding,
        onReorder = screenModel::reorder,
        onMove = screenModel::move,
        onMoveSeries = screenModel::moveSeries,
        onCancel = { screenModel.cancel(listOf(it)) },
        onCancelSeries = screenModel::cancelSeries,
        modifier = Modifier.nestedScroll(nestedScrollConnection),
    )
}
