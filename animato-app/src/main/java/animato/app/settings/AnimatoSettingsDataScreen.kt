package animato.app.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import animato.anime.backup.create.AnimatoBackupCreateJob
import animato.anime.backup.restore.AniyomiBackupRestoreJob
import animato.app.downloads.DownloadCleanupPreferences
import animato.app.downloads.OrphanedDownloadSweeper
import animato.ui.settings.PreferenceRowHorizontalPadding
import animato.ui.settings.PreferenceSubcomponentRow
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Mihon's data and storage settings, with the backup group answering for both libraries.
 *
 * Mihon's screen is not copied. Its own list is asked for and passed through, so the storage
 * location, the data and the export controls stay Mihon's and keep working the way Mihon changes
 * them. Two things are done to it.
 *
 * The backup group is swapped. Mihon's writes and reads manga, which on this app is half a backup,
 * and a control that silently does half of what it says is worse than one that is missing. The
 * replacement is the same three controls — create, restore, interval — pointed at jobs that cover
 * both halves.
 *
 * The Aniyomi import is appended. It sits here rather than in the anime section because an Aniyomi
 * backup holds a manga library too, and this is the screen someone opens when they are looking for
 * their library rather than for a setting.
 *
 * The group is found by its title. That is a real coupling and worth naming: if Mihon retitles its
 * backup group, this stops replacing it and starts appending a second one — visible immediately,
 * and a one-line fix.
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
        val backupGroupTitle = stringResource(MR.strings.label_backup)
        val storageGroupTitle = stringResource(MR.strings.pref_storage_usage)
        val cleanupItems = getCleanupItems()

        return SettingsDataScreen.getPreferences()
            .map { preference ->
                when {
                    preference !is Preference.PreferenceGroup -> preference

                    preference.title == backupGroupTitle -> getBackupGroup()

                    // Added to Mihon's storage group rather than put in a second group of the same
                    // name. Theirs shows what is using the disk; this is how some of it is given
                    // back, and the two belong together.
                    preference.title == storageGroupTitle ->
                        preference.copy(preferenceItems = preference.preferenceItems + cleanupItems)

                    else -> preference
                }
            }
            .plus(getAniyomiImportGroup())
    }

    /**
     * Reclaiming the disk that entries leave behind when they leave the library.
     *
     * Items rather than a group of their own, and here rather than in either download settings
     * screen, because this is the one cleanup that applies to both halves: Mihon's download screen
     * would only reach manga and ours only anime.
     */
    @Composable
    private fun getCleanupItems(): List<Preference.PreferenceItem<out Any, out Any>> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val cleanupPreferences = remember { Injekt.get<DownloadCleanupPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = cleanupPreferences.deleteWhenRemovedFromLibrary(),
                title = stringResource(AYMR.strings.pref_delete_removed_downloads),
                subtitle = stringResource(AYMR.strings.pref_delete_removed_downloads_summary),
            ),
            // Runs the same sweep the app runs at launch, for someone who has just turned the
            // setting on and would otherwise have to restart to see it do anything.
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_delete_removed_downloads_now),
                onClick = {
                    scope.launchIO {
                        val result = OrphanedDownloadSweeper().sweep()
                        withUIContext {
                            context.toast(
                                if (result.total == 0) {
                                    context.stringResource(AYMR.strings.delete_removed_downloads_none)
                                } else {
                                    context.stringResource(
                                        AYMR.strings.delete_removed_downloads_done,
                                        result.total,
                                    )
                                },
                            )
                        }
                    }
                },
            ),
        )
    }

    /**
     * Create, restore, and how often to do it by itself.
     *
     * Deliberately the same shape as Mihon's — two segmented buttons, an interval, a note — because
     * anyone arriving from Mihon should not have to learn a new screen to find their backups.
     */
    @Composable
    private fun getBackupGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val backupPreferences = remember { Injekt.get<BackupPreferences>() }
        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp.collectAsState()

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    return Intent.createChooser(
                        super.createIntent(context, input),
                        context.stringResource(MR.strings.file_select_backup),
                    )
                }
            },
        ) { uri ->
            if (uri == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }
            navigator.push(AniyomiImportScreen(uri.toString(), isAniyomiImport = false))
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_backup),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.label_backup),
                ) {
                    PreferenceSubcomponentRow {
                        MultiChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(intrinsicSize = IntrinsicSize.Min)
                                .padding(horizontal = PreferenceRowHorizontalPadding),
                        ) {
                            SegmentedButton(
                                modifier = Modifier.fillMaxHeight(),
                                checked = false,
                                onCheckedChange = { navigator.push(AnimatoCreateBackupScreen()) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) {
                                Text(stringResource(MR.strings.pref_create_backup))
                            }
                            SegmentedButton(
                                modifier = Modifier.fillMaxHeight(),
                                checked = false,
                                onCheckedChange = {
                                    val busy = AniyomiBackupRestoreJob.isRunning(context) ||
                                        BackupRestoreJob.isRunning(context)
                                    if (busy) {
                                        context.toast(MR.strings.restore_in_progress)
                                        return@SegmentedButton
                                    }
                                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                        context.toast(MR.strings.restore_miui_warning)
                                    }
                                    // Wrapped in a chooser, so there is always something to handle it.
                                    chooseBackup.launch("*/*")
                                },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) {
                                Text(stringResource(MR.strings.pref_restore_backup))
                            }
                        }
                    }
                },
                Preference.PreferenceItem.ListPreference(
                    preference = backupPreferences.backupInterval,
                    entries = mapOf(
                        0 to stringResource(MR.strings.off),
                        6 to stringResource(MR.strings.update_6hour),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(MR.strings.pref_backup_interval),
                    onValueChanged = {
                        AnimatoBackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.backup_info) + "\n\n" +
                        stringResource(AYMR.strings.backup_format_info) + "\n\n" +
                        stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
            ),
        )
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
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.aniyomi_import),
                    subtitle = stringResource(AYMR.strings.aniyomi_import_summary),
                    onClick = {
                        val busy = AniyomiBackupRestoreJob.isRunning(context) ||
                            BackupRestoreJob.isRunning(context)
                        if (busy) {
                            context.toast(AYMR.strings.aniyomi_import_in_progress)
                        } else {
                            chooseBackup.launch("*/*")
                        }
                    },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(AYMR.strings.aniyomi_import_info)),
            ),
        )
    }
}
