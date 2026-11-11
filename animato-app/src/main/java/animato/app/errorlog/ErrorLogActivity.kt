package animato.app.errorlog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import animato.ui.theme.AnimatoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * A saved error log, shown over the app instead of handed to whichever text viewer is installed.
 * The update and restore notifications open their logs as plain text; this is where they land.
 */
class ErrorLogActivity : ComponentActivity() {

    private var log by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data ?: return finish()

        lifecycleScope.launch {
            log = withContext(Dispatchers.IO) { read(uri) }
        }

        setContent {
            AnimatoTheme {
                AlertDialog(
                    onDismissRequest = ::finish,
                    title = { Text(stringResource(AYMR.strings.error_log_title)) },
                    text = {
                        SelectionContainer(
                            modifier = Modifier
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = log.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = ::finish) { Text(stringResource(MR.strings.action_close)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { share(uri) }) { Text(stringResource(MR.strings.action_share)) }
                        TextButton(onClick = ::copy) { Text(stringResource(MR.strings.action_copy_to_clipboard)) }
                    },
                )
            }
        }
    }

    private fun read(uri: Uri): String =
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty().let { if (it.length > MAX_CHARS) it.take(MAX_CHARS) + "\n…" else it }

    private fun copy() {
        val text = log ?: return
        getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(null, text))
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    private companion object {
        // A whole library failing is a long file; past this it is the same few reasons repeated
        const val MAX_CHARS = 200_000
    }
}
