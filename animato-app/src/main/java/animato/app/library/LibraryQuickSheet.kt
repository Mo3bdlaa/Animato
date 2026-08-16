package animato.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import animato.domain.content.ContentType
import animato.ui.components.Pill
import animato.ui.entries.ItemCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Long-press a cover: four things, each captioned with what it will do.
 *
 * The captions are the point. *Mark done up to here* with no number is a button people press once
 * and then check the shelf to find out what happened; *Marks 253 items as done* is a sentence you
 * can decline. Every row here names its consequence rather than its verb, which is also why the
 * sheet reads before it draws its captions rather than guessing from the library row.
 *
 * Continue is the only blue glyph, because it is the only one that resumes something. Remove is not
 * red: the danger lives in the confirmation, and a label coloured like a warning makes the other
 * three look safer than they are rather than making this one look dangerous.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryQuickSheet(
    sheet: QuickSheetState,
    onDismiss: () -> Unit,
    onContinue: (Long) -> Unit,
    onOpen: () -> Unit,
    onMarkDone: () -> Unit,
    onDownloadNext: () -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    var confirmingRemoval by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius),
    ) {
        Column(modifier = Modifier.padding(bottom = MaterialTheme.padding.large)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemCover.Square(
                    data = sheet.entry.coverData,
                    contentDescription = sheet.entry.title,
                    modifier = Modifier.size(ThumbSize),
                    shape = RoundedCornerShape(ThumbRadius),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sheet.entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(AYMR.strings.quick_unviewed, sheet.unviewedCount.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(
                    text = stringResource(
                        when (sheet.entry.contentType) {
                            ContentType.MANGA -> AYMR.strings.label_manga
                            ContentType.ANIME -> AYMR.strings.label_anime
                        },
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            ActionRow(
                icon = Icons.Outlined.PlayArrow,
                iconTint = MaterialTheme.colorScheme.primary,
                title = stringResource(AYMR.strings.label_continue),
                // The next item's own name, so the row says which one rather than promising "next".
                caption = sheet.nextItemName ?: stringResource(AYMR.strings.quick_nothing_left),
                enabled = sheet.canContinue,
                onClick = { sheet.nextItemId?.let(onContinue) },
            )
            ActionRow(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(AYMR.strings.quick_mark_done),
                caption = stringResource(
                    AYMR.strings.quick_mark_done_caption,
                    (sheet.entry.totalItems - sheet.unviewedCount).toString(),
                ),
                enabled = !sheet.isLoading,
                onClick = onMarkDone,
            )
            ActionRow(
                icon = Icons.Outlined.Download,
                title = stringResource(
                    AYMR.strings.quick_download_next,
                    LibraryQuickActions.DOWNLOAD_BATCH.toString(),
                ),
                caption = stringResource(
                    AYMR.strings.quick_download_caption,
                    minOf(sheet.unviewedCount, LibraryQuickActions.DOWNLOAD_BATCH).toString(),
                ),
                enabled = sheet.unviewedCount > 0,
                onClick = onDownloadNext,
            )
            ActionRow(
                icon = Icons.Outlined.Delete,
                title = stringResource(MR.strings.action_remove),
                caption = stringResource(
                    AYMR.strings.quick_remove_caption,
                    sheet.downloadedCount.toString(),
                ),
                enabled = true,
                onClick = { confirmingRemoval = true },
            )
        }
    }

    if (confirmingRemoval) {
        RemovalDialog(
            title = sheet.entry.title,
            downloadedCount = sheet.downloadedCount,
            onDismiss = { confirmingRemoval = false },
            onConfirm = { deleteDownloads ->
                confirmingRemoval = false
                onRemove(deleteDownloads)
            },
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint.copy(alpha = alpha))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
}

/**
 * Two questions, asked separately.
 *
 * Taking something off a shelf is trivially undone by adding it again; deleting the files is not.
 * So the checkbox starts unticked and the files are kept unless somebody says otherwise — a
 * confirmation that silently does both is not a confirmation of either.
 */
@Composable
private fun RemovalDialog(
    title: String,
    downloadedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var deleteDownloads by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.action_remove)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(stringResource(AYMR.strings.quick_remove_confirm, title))
                if (downloadedCount > 0) {
                    Row(
                        modifier = Modifier.clickable { deleteDownloads = !deleteDownloads },
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = deleteDownloads, onCheckedChange = null)
                        Text(
                            stringResource(
                                AYMR.strings.quick_remove_delete_downloads,
                                downloadedCount.toString(),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteDownloads) }) {
                Text(stringResource(MR.strings.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private const val DISABLED_ALPHA = 0.38f
private val ThumbSize = 56.dp
private val ThumbRadius = 12.dp
private val SheetRadius = 28.dp
