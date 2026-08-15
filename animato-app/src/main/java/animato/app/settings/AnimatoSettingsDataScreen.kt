package animato.app.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import animato.anime.backup.restore.AniyomiBackupRestoreJob
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Mihon's data and storage settings, with the Aniyomi import under them.
 *
 * Mihon's screen is not copied — its own list is asked for and passed through, so every control on
 * it stays Mihon's and keeps working the way Mihon changes it. One group is appended.
 *
 * The import belongs here rather than in the anime section because it is not an anime feature: an
 * Aniyomi backup holds a manga library too, and this is the screen someone goes to when they are
 * looking for their library rather than for a setting.
 */
object AnimatoSettingsDataScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.label_data_storage

    @Composable
    override fun RowScope.AppBarAction() {
        with(SettingsDataScreen) { AppBarAction() }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        return SettingsDataScreen.getPreferences() + getAniyomiImportGroup()
    }

    @Composable
    private fun getAniyomiImportGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    return Intent.createChooser(
                        super.createIntent(context, input),
                        context.stringResource(AYMR.strings.aniyomi_import_select_file),
                    )
                }
            },
        ) { uri ->
            if (uri == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }
            navigator.push(AniyomiImportScreen(uri.toString()))
        }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.aniyomi_import),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.aniyomi_import),
                    subtitle = stringResource(AYMR.strings.aniyomi_import_summary),
                    onClick = {
                        // An import and a Mihon restore share one work slot, so the one already
                        // running is the one that finishes.
                        val busy = AniyomiBackupRestoreJob.isRunning(context) ||
                            BackupRestoreJob.isRunning(context)
                        if (busy) {
                            context.toast(AYMR.strings.aniyomi_import_in_progress)
                        } else {
                            // Wrapped in a chooser, so there is always something to handle it.
                            chooseBackup.launch("*/*")
                        }
                    },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(AYMR.strings.aniyomi_import_info)),
            ),
        )
    }
}
