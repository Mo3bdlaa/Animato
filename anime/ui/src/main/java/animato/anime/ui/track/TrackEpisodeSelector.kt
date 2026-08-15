package animato.anime.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.WheelNumberPicker
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Picks how many episodes have been watched, when correcting a tracker by hand.
 *
 * Mihon's equivalent is `TrackChapterSelector`, which says "chapters" and builds on a `BaseSelector`
 * private to its file. Aniyomi's answer was to widen Mihon's into a `TrackItemSelector` taking an
 * `isManga` flag; ours is a separate composable that says "episodes", so Mihon's dialog stays as it
 * is and neither has to ask what it is being used for.
 */
@Composable
fun TrackEpisodeSelector(
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    range: Iterable<Int>,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialogContent(
        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
        title = { Text(text = stringResource(AYMR.strings.episodes)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                WheelNumberPicker(
                    items = range.toList(),
                    modifier = Modifier.align(Alignment.Center),
                    startIndex = selection,
                    onSelectionChanged = { onSelectionChange(it) },
                )
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small, Alignment.End),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )
}
