package animato.app.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.app.settings.AnimatoSettingsScreen
import animato.ui.theme.LocalAnimatoPalette
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tracking accounts, in one list.
 *
 * Tracking used to be two settings screens — Mihon's manga trackers and ours for anime — which are
 * lists of the *same accounts* with the same logins underneath. So *am I signed in to AniList*
 * depended on which screen you happened to be on, and *is anything actually syncing* was answered
 * nowhere at all.
 *
 * Signed-in and signed-out rows are the same height, and the difference between them is a caption
 * plus one trailing control. Two visual languages for one list would imply the disconnected
 * accounts are broken rather than simply optional.
 *
 * Per-title binding is deliberately absent — that lives on the title page, where the title is. This
 * screen is accounts and nothing else.
 */
class TrackingHubScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = viewModel { TrackingHubScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    titleContent = { AppBarTitle(stringResource(MR.strings.manga_tracking_tab)) },
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (state.signedIn.isNotEmpty()) {
                            TextButton(onClick = screenModel::syncAll) {
                                Text(stringResource(AYMR.strings.tracking_sync_all))
                            }
                        }
                    },
                )
            },
        ) { contentPadding ->
            if (state.isLoading) {
                LoadingScreen(Modifier.padding(contentPadding))
                return@Scaffold
            }

            LazyColumn(
                contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
            ) {
                items(items = state.accounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        onSync = { screenModel.sync(account) },
                        onSignIn = { navigator.push(AnimatoSettingsScreen()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: TrackingAccount,
    onSync: () -> Unit,
    onSignIn: () -> Unit,
) {
    val palette = LocalAnimatoPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.caption(),
                style = MaterialTheme.typography.bodySmall,
                color = if (account.failure != null) palette.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            account.isSyncing -> Box(
                modifier = Modifier.size(SpinnerBoxSize),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(SpinnerSize))
            }
            // Signed in gets the outlined control and signed out gets the filled one, because
            // signing in is the thing worth doing on a row that cannot do anything else yet.
            account.isLoggedIn -> OutlinedButton(onClick = onSync) {
                Text(stringResource(AYMR.strings.tracking_sync_now))
            }
            else -> Button(onClick = onSignIn) {
                Text(stringResource(MR.strings.login))
            }
        }
    }
}

/**
 * The account's state as a sentence.
 *
 * A count with no time is a number that might be a year old; a time with no count says nothing
 * about whether the account has anything in it. Both, in that order, or the one honest sentence
 * that applies when there is neither.
 */
@Composable
private fun TrackingAccount.caption(): String = when {
    failure != null -> stringResource(AYMR.strings.tracking_failed, failure)
    !isLoggedIn -> stringResource(AYMR.strings.tracking_not_signed_in)
    titles == 0 -> stringResource(AYMR.strings.tracking_no_titles)
    lastSyncAt == 0L -> stringResource(AYMR.strings.tracking_titles, titles.toString())
    else -> stringResource(
        AYMR.strings.tracking_titles_synced,
        titles.toString(),
        relativeTime(System.currentTimeMillis() - lastSyncAt),
    )
}

/**
 * "4m", "2h", "3d".
 *
 * Deliberately coarse. A tracker sync is not an event anybody times to the second, and a caption
 * that reads *synced 47 seconds ago* invites a precision the number does not have — the timestamp
 * is written when the last title finished, not when the account was actually current.
 */
private fun relativeTime(elapsedMillis: Long): String {
    val duration = elapsedMillis.milliseconds
    return when {
        duration.inWholeMinutes < 1 -> "<1m"
        duration.inWholeHours < 1 -> "${duration.inWholeMinutes}m"
        duration.inWholeDays < 1 -> "${duration.inWholeHours}h"
        else -> "${duration.inWholeDays}d"
    }
}

private val RowHeight = 72.dp
private val SpinnerBoxSize = 48.dp
private val SpinnerSize = 20.dp
