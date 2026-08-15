package animato.ui.browse

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * Pieces of the extensions screen that both content types draw the same way.
 *
 * In Mihon these live inside `MangaExtensionsScreen.kt` and `MangaExtensionDetailsScreen.kt` as
 * file-private or module-internal declarations — fine when the manga and anime screens were in one
 * module, and unreachable once the anime screens are in another. They are copies here rather than
 * a shared abstraction pushed back into Mihon's files, which is the rule the architecture keeps.
 *
 * `TrailingWidgetBuffer` is the same story: an `internal val` in Mihon's preference widgets, and
 * the extension detail rows need it to line their trailing controls up with the settings screens.
 */

/** Section heading in an extension list — "Installed", "Available", and so on. */
@Composable
fun ExtensionHeader(
    textRes: StringResource,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    ExtensionHeader(
        text = stringResource(textRes),
        modifier = modifier,
        action = action,
    )
}

@Composable
fun ExtensionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        action()
    }
}

/**
 * Shown before an extension that was not installed from a trusted store is allowed to run.
 *
 * Extensions execute code inside the app, so this is a security prompt rather than a formality.
 */
@Composable
fun ExtensionTrustDialog(
    onClickConfirm: () -> Unit,
    onClickDismiss: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(MR.strings.untrusted_extension)) },
        text = { Text(text = stringResource(MR.strings.untrusted_extension_message)) },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(MR.strings.ext_trust))
            }
        },
        dismissButton = {
            TextButton(onClick = onClickDismiss) {
                Text(text = stringResource(MR.strings.ext_uninstall))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

/** Confirms the user meant to enable an 18+ source before its content appears anywhere. */
@Composable
fun NsfwWarningDialog(
    onClickConfirm: () -> Unit,
) {
    AlertDialog(
        text = {
            Text(
                text = stringResource(MR.strings.ext_nsfw_warning),
                modifier = Modifier.secondaryItemAlpha(),
            )
        },
        confirmButton = {
            TextButton(onClick = onClickConfirm) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        onDismissRequest = onClickConfirm,
    )
}

/** Gap that keeps a row's trailing control aligned with the settings screens' widgets. */
val TrailingWidgetBuffer = 16.dp
