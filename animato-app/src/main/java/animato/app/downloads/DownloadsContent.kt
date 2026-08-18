package animato.app.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.navigation.LensButton
import animato.domain.content.ContentType
import animato.ui.components.AnimatoEmptyState
import animato.ui.components.Pill
import animato.ui.navigation.AnimatoNavigator
import animato.ui.navigation.AnimatoTab
import animato.ui.theme.LocalAnimatoPalette
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

/**
 * The queue as a pushed screen, which is what it is now.
 *
 * It used to hold a slot in the bottom bar; sources took that slot, on the reasoning in
 * `AnimatoSourcesTab`. This is where it went — one tap from the Updates top bar, behind an icon
 * that only appears when there is something in it, and from the launcher shortcut.
 */
class DownloadsScreen : Screen() {

    @Composable
    override fun Content() = DownloadsContent(canGoBack = true)
}

/**
 * One queue, both halves, and the number that says why it matters.
 *
 * Downloads was whichever half the lens happened to point at. A queue is a claim about what the
 * device is doing right now, and two of them cannot both be that — pausing meant pausing twice, and
 * an anime download stuck at 3% was invisible from the manga queue.
 *
 * The storage line is one row and not a card. A donut chart of somebody's own storage is
 * decoration; the number and the verb next to it are the whole content.
 *
 * Blue is spent on the one active download and nowhere else. A queue drawn as four blue tracks at
 * 0% reads as four things happening when one is, so queued rows carry a muted position number
 * instead.
 */
@Composable
internal fun DownloadsContent(canGoBack: Boolean = false) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val screenModel = viewModel { DownloadsScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                titleContent = { AppBarTitle(stringResource(MR.strings.label_download_queue)) },
                navigateUp = if (canGoBack) ({ navigator.pop() }) else null,
                scrollBehavior = scrollBehavior,
                actions = {
                    LensButton()
                    if (state.rows.isNotEmpty()) {
                        // One global control, in the bar. A pause button on every row plus a global
                        // one is two ways to do the same thing that disagree about scope.
                        IconButton(onClick = screenModel::pauseOrResume) {
                            Icon(
                                imageVector = if (state.isRunning) {
                                    Icons.Outlined.Pause
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (state.isRunning) MR.strings.action_pause else MR.strings.action_resume,
                                ),
                            )
                        }
                        TextButton(onClick = screenModel::clearQueue) {
                            Text(stringResource(MR.strings.action_cancel_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
            return@Scaffold
        }

        LazyColumn(
            contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
        ) {
            item(key = "storage") {
                StorageLine(
                    storage = state.storage,
                    isCleaning = state.isCleaning,
                    onCleanUp = {
                        screenModel.cleanUp { result ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(
                                        AYMR.strings.cleanup_removed,
                                        result.total.toString(),
                                    ),
                                )
                            }
                        }
                    },
                )
            }

            if (state.rows.isEmpty()) {
                item(key = "empty") { NothingDownloading() }
                return@LazyColumn
            }

            section("active", AYMR.strings.label_downloading, state.active) { row, _ ->
                ActiveRow(row = row, onCancel = { screenModel.cancel(row) })
            }
            section("queued", AYMR.strings.label_queued, state.queued) { row, index ->
                QueuedRow(row = row, position = index + 1, onCancel = { screenModel.cancel(row) })
            }
            section("failed", AYMR.strings.label_failed, state.failed) { row, _ ->
                FailedRow(
                    row = row,
                    onRetry = screenModel::retry,
                    onCancel = { screenModel.cancel(row) },
                )
            }
        }
    }
}

private fun LazyListScope.section(
    id: String,
    titleRes: StringResource,
    rows: List<DownloadRow>,
    content: @Composable (DownloadRow, Int) -> Unit,
) {
    if (rows.isEmpty()) return

    item(key = "header-$id") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // The count belongs to the heading it counts, which is the only place a number here is
            // information rather than furniture.
            Text(
                text = rows.size.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    itemsIndexed(items = rows, key = { _, row -> row.key }) { index, row ->
        content(row, index)
    }
}

/**
 * How much is on disk, and the one thing to do about it.
 *
 * The size arrives late — it is a directory walk — so the line is drawn without it and gains it,
 * rather than the screen waiting. An em dash while it counts is honest; a zero would not be.
 */
@Composable
private fun StorageLine(
    storage: StorageSummary,
    isCleaning: Boolean,
    onCleanUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = storage.bytes?.let { formatBytes(it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(AYMR.strings.storage_items, storage.items.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onCleanUp, enabled = !isCleaning) {
            Text(stringResource(AYMR.strings.action_clean_up))
        }
    }
}

/** The one download that is happening. The only blue on the screen. */
@Composable
private fun ActiveRow(row: DownloadRow, onCancel: () -> Unit) {
    Column {
        BaseRow(row = row, trailing = { CancelButton(onCancel) })
        LinearProgressIndicator(
            progress = { row.progress / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium)
                .height(ProgressHeight),
        )
    }
}

/** Waiting, with its place in the line. A muted numeral rather than an empty blue track. */
@Composable
private fun QueuedRow(row: DownloadRow, position: Int, onCancel: () -> Unit) {
    BaseRow(
        row = row,
        leading = {
            Box(modifier = Modifier.size(PositionBoxSize), contentAlignment = Alignment.Center) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailing = { CancelButton(onCancel) },
    )
}

/**
 * Something that did not download.
 *
 * The design sheet asks for the reason in words — *Source returned 403* — and neither half stores
 * one: a failed download keeps an error status and nothing else, with the detail going to the
 * downloader's own notification. Rather than inventing a reason, the row says it failed and offers
 * the action. The reason arrives when the downloaders carry it, not before.
 */
@Composable
private fun FailedRow(row: DownloadRow, onRetry: () -> Unit, onCancel: () -> Unit) {
    val palette = LocalAnimatoPalette.current
    BaseRow(
        row = row,
        caption = {
            Text(
                text = stringResource(AYMR.strings.download_failed),
                style = MaterialTheme.typography.bodySmall,
                color = palette.error,
                maxLines = 1,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(MR.strings.action_retry))
                }
                CancelButton(onCancel)
            }
        },
    )
}

@Composable
private fun BaseRow(
    row: DownloadRow,
    leading: @Composable (() -> Unit)? = null,
    caption: @Composable (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(
                    text = stringResource(
                        when (row.contentType) {
                            ContentType.MANGA -> AYMR.strings.label_manga
                            ContentType.ANIME -> AYMR.strings.label_anime
                        },
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            caption?.invoke() ?: Text(
                text = row.itemName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    IconButton(onClick = onCancel) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = stringResource(MR.strings.action_cancel),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An empty queue, which is the normal state.
 *
 * It keeps the storage line above it — what is already downloaded has not gone anywhere — and
 * teaches the batch gesture in the sentence, because an empty queue with no way forward is a dead
 * end and there is no screen in this app that is allowed to be one.
 *
 * It was a lone grey sentence in the middle of a black screen, which is what a broken screen looks
 * like. The design sheet has been specific about this from the start — mark, one sentence, one
 * button — and this is the shared component that finally obeys it. The button goes to the library,
 * because that is where the gesture the sentence describes actually lives.
 */
@Composable
private fun NothingDownloading() {
    AnimatoEmptyState(
        message = stringResource(AYMR.strings.downloads_nothing_downloading),
        actionLabel = stringResource(MR.strings.label_library),
        onAction = { AnimatoNavigator.openTab(AnimatoTab.LIBRARY) },
    )
}

/**
 * Bytes, in the unit a person would say out loud.
 *
 * Binary units with decimal names is the convention every file manager on the platform uses, and
 * matching what the device says about the same folder matters more here than being right about
 * kibibytes.
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < UNIT) return "$bytes B"
    val exponent = (Math.log(bytes.toDouble()) / Math.log(UNIT.toDouble())).toInt().coerceAtMost(4)
    val value = bytes / Math.pow(UNIT.toDouble(), exponent.toDouble())
    return "%.1f %sB".format(value, "KMGT"[exponent - 1])
}

private const val UNIT = 1024L
private val ProgressHeight = 3.dp
private val PositionBoxSize = 24.dp
