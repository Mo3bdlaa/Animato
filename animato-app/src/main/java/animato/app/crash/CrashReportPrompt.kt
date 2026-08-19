package animato.app.crash

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * *Animato closed unexpectedly last time.* — asked once, on the launch after a crash.
 *
 * ## Why the prompt rather than a list somewhere
 *
 * A crash report is only worth keeping if somebody sends it, and nobody goes looking in Settings
 * for a screen they have no reason to believe exists. The moment somebody will act on it is the
 * moment they notice the app restarted, which is this one.
 *
 * Asked **once**: dismissing it clears the flag. A dialog that returns every launch until it gets
 * what it wants is a dialog people learn to dismiss without reading, which costs the next crash
 * as well as this one.
 *
 * ## What sharing does
 *
 * Hands the text to Android's share sheet. Where it goes from there is the person's choice —
 * nothing is uploaded, and there is nowhere for it to be uploaded *to*. See [CrashRecorder].
 */
@Composable
fun CrashReportPrompt() {
    val context = LocalContext.current
    // Read once, when the screen is first composed. A crash that happened while this composition
    // was alive would be a crash this process did not survive to draw anything after.
    var pending by remember { mutableStateOf(CrashRecorder.pending() != null) }
    if (!pending) return

    val report = remember { CrashRecorder.report(Injekt.get()) }
    if (report.isNullOrBlank()) {
        CrashRecorder.acknowledge()
        return
    }

    val dismiss = {
        CrashRecorder.acknowledge()
        pending = false
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(AYMR.strings.crash_prompt_title)) },
        text = {
            Text(
                // The first line of the trace, which is the exception and its message. Enough to
                // recognise a crash already reported; short enough not to become the dialog.
                text = report.lineSequence()
                    .drop(HEADER_LINES)
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?: stringResource(AYMR.strings.crash_prompt_body),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    ContextCompat.startActivity(
                        context,
                        Intent.createChooser(intent, context.getString(MR.strings.action_share.resourceId)),
                        null,
                    )
                    dismiss()
                },
            ) {
                Text(stringResource(MR.strings.action_share))
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss) {
                Text(stringResource(MR.strings.action_close))
            }
        },
    )
}

/** Version, Android, device and time — see [CrashRecorder]. The exception is after them. */
private const val HEADER_LINES = 4
