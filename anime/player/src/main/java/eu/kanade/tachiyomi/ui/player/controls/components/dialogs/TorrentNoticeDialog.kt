package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * What BitTorrent does that nobody expects, said once, before it starts doing it.
 *
 * The words are the ones this app has always used for the TorrServer switch. What changed is when
 * they appear: the switch now ships on, so the disclosure moved to the moment a torrent is first
 * about to play. Declining is a real answer — it leaves the setting alone and nothing is fetched
 * or shared — which is why the dialog cannot be dismissed into silence and has to be answered.
 */
@Composable
fun TorrentNoticeDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(AYMR.strings.pref_player_torrents_notice)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                Text(stringResource(AYMR.strings.pref_player_torrents_notice_text))
                Text(stringResource(AYMR.strings.pref_player_torrents_notice_footer))
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
