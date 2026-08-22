package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.player.components.PlayerSheet
import eu.kanade.presentation.player.components.SwitchPreference
import eu.kanade.tachiyomi.ui.player.ArtType
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.PlayerDialog
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ActionButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.io.InputStream

@Composable
fun ScreenshotSheet(
    isLocalSource: Boolean,
    hasSubTracks: Boolean,
    showSubtitles: Boolean,
    onToggleShowSubtitles: (Boolean) -> Unit,

    cachePath: String,
    onSetAsArt: (ArtType, (() -> InputStream)) -> Unit,
    onShare: (() -> InputStream) -> Unit,
    onSave: (() -> InputStream) -> Unit,
    takeScreenshot: (String, Boolean) -> InputStream?,

    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var setArtTypeAs: ArtType? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    /**
     * The capture, or a message saying it did not happen.
     *
     * mpv returns null here for reasons that are ordinary rather than exceptional — a frame it
     * cannot read back under hardware decoding, a stream that has not decoded one yet, which is
     * most of the first seconds of a torrent. Every one of those used to be `!!`, so a screenshot
     * that could not be taken took the app down with it.
     */
    fun captured(action: (() -> InputStream) -> Unit) {
        val stream = takeScreenshot(cachePath, showSubtitles)
        if (stream == null) {
            context.toast(AYMR.strings.screenshot_failed)
        } else {
            action { stream }
        }
    }

    PlayerSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(top = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Cover },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(AYMR.strings.set_as_background),
                    icon = Icons.Outlined.Photo,
                    onClick = { setArtTypeAs = ArtType.Background },
                )
                if (isLocalSource) {
                    ActionButton(
                        modifier = Modifier.weight(1f),
                        title = stringResource(AYMR.strings.set_as_thumbnail),
                        icon = Icons.Outlined.Photo,
                        onClick = { setArtTypeAs = ArtType.Thumbnail },
                    )
                }
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        captured(onShare)
                    },
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        captured(onSave)
                    },
                )
            }

            if (hasSubTracks) {
                SwitchPreference(
                    value = showSubtitles,
                    onValueChange = onToggleShowSubtitles,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.medium),
                    content = {
                        Text(
                            text = stringResource(AYMR.strings.screenshot_show_subs),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
        }
    }

    if (setArtTypeAs != null) {
        PlayerDialog(
            title = stringResource(MR.strings.confirm_set_image_as_cover),
            modifier = Modifier.fillMaxWidth(fraction = 0.6F).padding(MaterialTheme.padding.medium),
            onConfirmRequest = {
                setArtTypeAs?.let { type -> captured { stream -> onSetAsArt(type, stream) } }
            },
            onDismissRequest = { setArtTypeAs = null },
        )
    }
}
