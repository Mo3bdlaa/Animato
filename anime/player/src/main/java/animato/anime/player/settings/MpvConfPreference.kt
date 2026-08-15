package animato.anime.player.settings

import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import animato.anime.player.getMPVConfigDirectory
import animato.ui.settings.EditTextDialogRow
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.preference.Preference as PreferenceData

/**
 * An mpv configuration file, edited in the app.
 *
 * mpv reads plain text files from its config directory, and these settings are the app's way of
 * writing them without a file manager. The preference is the source of truth for what the app
 * shows; [fileName] is where mpv will actually look, so both are written.
 *
 * The write is conditional on all-files access, which is what the config directory sits behind on
 * Android 11 and later. Without it the file cannot be written, and writing only the preference
 * would leave the app showing a configuration mpv is not using — so neither is written, and the
 * setting stays where it was.
 *
 * Aniyomi expressed this as a variant of Mihon's sealed `PreferenceItem`. This is the same
 * behaviour through `CustomPreference`, which is the extension point that hierarchy already has.
 */
fun mpvConfPreference(
    preference: PreferenceData<String>,
    title: String,
    fileName: String? = null,
    icon: ImageVector? = null,
    storageManager: StorageManager = Injekt.get(),
) = Preference.PreferenceItem.CustomPreference(title) {
    val value by preference.collectAsState()
    EditTextDialogRow(
        title = title,
        // The stored value, not a format string: a config file's first lines are the subtitle.
        subtitle = value.previewLines(),
        icon = icon,
        value = value,
        canBeBlank = true,
        singleLine = false,
        onConfirm = { newValue ->
            val written = withIOContext { storageManager.writeMpvConf(fileName, newValue) }
            if (written) preference.set(newValue)
            written
        },
    )
}

/** The first couple of lines, so the row says what the file currently holds. */
private fun String.previewLines(): String {
    val lines = lines()
    return lines.take(PREVIEW_LINES).joinToString(
        separator = "\n",
        postfix = if (lines.size > PREVIEW_LINES) "\n…" else "",
    )
}

private fun StorageManager.writeMpvConf(fileName: String?, value: String): Boolean {
    if (fileName == null) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) return false

    val file = getMPVConfigDirectory()?.createFile(fileName) ?: return false
    file.openOutputStream().bufferedWriter().use { it.write(value) }
    return true
}

private const val PREVIEW_LINES = 2
