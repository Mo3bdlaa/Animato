package animato.app.crash

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import animato.app.updater.AnimatoAppUpdateChecker
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/** What the update check came back with. */
private sealed interface UpdateCheck {
    data object Checking : UpdateCheck
    data class Available(val version: String, val link: String) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * The screen shown instead of an app that will not open.
 *
 * ## What it must not do
 *
 * Anything that could fail. This is reached precisely because the ordinary startup path is
 * throwing, so it deliberately does not touch the theme, the navigator, the database, the source
 * manager or any of the things that might be the reason it is here. Plain Material colours, one
 * network call, and everything wrapped.
 *
 * ## Why the update check is the whole point
 *
 * A crash on the way in disables the mechanism for delivering the fix for it — the update check
 * lives inside an app that never gets far enough to run one. So this runs that check first, before
 * anything else is attempted, which is the only order in which a broken build can reach the release
 * that repairs it.
 *
 * *Open anyway* stays available regardless. Being told the app is broken and then not being allowed
 * to try is worse than the crash, and the person on the device knows things this code does not —
 * that they only need to reach one screen, that it worked a minute ago, that they will risk it.
 */
@Composable
fun RecoveryScreen(
    onOpenAnyway: () -> Unit,
) {
    val context = LocalContext.current
    var check: UpdateCheck by remember { mutableStateOf(UpdateCheck.Checking) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        check = UpdateCheck.Checking
        check = try {
            val release = AnimatoAppUpdateChecker().checkForUpdate()
            when {
                release == null -> UpdateCheck.UpToDate
                else -> UpdateCheck.Available(release.version, release.downloadLink ?: release.releaseLink)
            }
        } catch (e: Throwable) {
            // Including Errors. Nothing below this screen catches anything, and a recovery screen
            // that crashes is worse than the crash it is recovering from.
            UpdateCheck.Failed
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(AYMR.strings.recovery_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(AYMR.strings.recovery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val state = check) {
                UpdateCheck.Checking -> {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(AYMR.strings.recovery_checking),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is UpdateCheck.Available -> {
                    Text(
                        text = stringResource(AYMR.strings.recovery_update_found, state.version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { context.openSafely(state.link) },
                    ) {
                        Text(stringResource(MR.strings.update_check_confirm))
                    }
                }

                UpdateCheck.UpToDate -> Text(
                    text = stringResource(AYMR.strings.recovery_update_none),
                    style = MaterialTheme.typography.bodyMedium,
                )

                UpdateCheck.Failed -> {
                    Text(
                        text = stringResource(AYMR.strings.recovery_update_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { attempt++ },
                    ) {
                        Text(stringResource(AYMR.strings.recovery_action_retry))
                    }
                }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenAnyway,
            ) {
                Text(stringResource(AYMR.strings.recovery_action_open))
            }

            // Last, and quiet: the report is what makes the next fix possible, but somebody staring
            // at an app that will not open wants it open, not to file something.
            TextButton(onClick = { context.shareCrashReport() }) {
                Text(stringResource(AYMR.strings.recovery_action_share))
            }
        }
    }
}

private fun Context.openSafely(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
}

private fun Context.shareCrashReport() {
    runCatching {
        val report = CrashRecorder.report(applicationContext as android.app.Application) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, null))
    }
}
