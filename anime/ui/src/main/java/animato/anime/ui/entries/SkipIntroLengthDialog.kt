package animato.anime.ui.entries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.WheelTextPicker
import tachiyomi.presentation.core.i18n.stringResource

/**
 * How many seconds the player skips when the user taps "skip intro" on this anime.
 *
 * Aniyomi declared this inside its player-settings screen object, which made an entry screen reach
 * into a settings screen to open a dialog. It is a per-anime setting shown from the anime page, so
 * it lives with the anime screens; phase 6d's player settings will call the same one.
 */
@Composable
fun SkipIntroLengthDialog(
    initialSkipIntroLength: Int,
    onDismissRequest: () -> Unit,
    onValueChanged: (skipIntroLength: Int) -> Unit,
) {
    val skipIntroLengthValue by rememberSaveable { mutableStateOf(initialSkipIntroLength) }
    var newLength = 0
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.pref_intro_length)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                WheelTextPicker(
                    modifier = Modifier.align(Alignment.Center),
                    items = remember { 0..255 }.map { stringResource(MR.strings.seconds_short, it) },
                    onSelectionChanged = { newLength = it },
                    startIndex = skipIntroLengthValue,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onValueChanged(newLength) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
