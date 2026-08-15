package animato.anime.ui.updates

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Confirms deleting the episodes selected in the updates list.
 *
 * Mihon's `UpdatesDeleteConfirmationDialog` asks about chapters, in those words. Aniyomi widened it
 * with an `isManga` flag; this is the anime one instead, so Mihon's dialog keeps saying exactly what
 * it means.
 */
@Composable
fun EpisodesDeleteConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(text = stringResource(AYMR.strings.confirm_delete_episodes))
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
