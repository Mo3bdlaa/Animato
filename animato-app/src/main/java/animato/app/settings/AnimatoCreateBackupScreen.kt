package animato.app.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import animato.anime.backup.create.AnimatoBackupCreateJob
import animato.anime.backup.create.AnimatoBackupCreator
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Choosing what goes into a backup.
 *
 * Mihon's screen with the labels corrected. Its options are reused as they are — the same set of
 * switches governs both libraries, because nobody wants to decide separately whether to back up
 * their chapters and their episodes — but its labels say "Manga" and "Chapters", and here each
 * switch covers both halves.
 */
class AnimatoCreateBackupScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<AnimatoCreateBackupViewModel>()
        val state by viewModel.state.collectAsState()

        val chooseBackupFile = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/*"),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.createBackup(context, uri)
                navigator.pop()
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.pref_create_backup),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = stringResource(MR.strings.action_create),
                actionEnabled = state.options.canCreate(),
                onClickAction = {
                    if (AnimatoBackupCreateJob.isManualJobRunning(context)) {
                        context.toast(MR.strings.backup_in_progress)
                        return@LazyColumnWithAction
                    }
                    try {
                        chooseBackupFile.launch(AnimatoBackupCreator.filename(context))
                    } catch (_: ActivityNotFoundException) {
                        context.toast(MR.strings.file_picker_error)
                    }
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item { WarningBanner(MR.strings.restore_miui_warning) }
                }

                item {
                    SectionCard(MR.strings.label_library) {
                        Options(libraryOptions, state.options, viewModel)
                    }
                }
                item {
                    SectionCard(MR.strings.label_settings) {
                        Options(BackupOptions.settingsOptions, state.options, viewModel)
                    }
                }
                item {
                    SectionCard {
                        Text(
                            modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            text = stringResource(AYMR.strings.backup_format_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun Options(
        options: List<BackupOptions.Entry>,
        current: BackupOptions,
        model: AnimatoCreateBackupViewModel,
    ) {
        options.forEach { option ->
            LabeledCheckbox(
                label = stringResource(option.label),
                checked = option.getter(current),
                onCheckedChange = { model.toggle(option.setter, it) },
                enabled = option.enabled(current),
            )
        }
    }

    /**
     * Mihon's library options with the labels that say what they actually do here.
     *
     * The getters and setters are Mihon's, untouched — only the words change, because a switch
     * called "Manga" that also decides whether the anime comes across is a lie.
     */
    private val libraryOptions = BackupOptions.libraryOptions.map { option ->
        when (option.label) {
            MR.strings.manga -> option.copy(label = MR.strings.label_library)
            MR.strings.chapters -> option.copy(label = AYMR.strings.backup_chapters_and_episodes)
            else -> option
        }
    }
}

class AnimatoCreateBackupViewModel : ViewModel() {

    val state: StateFlow<AnimatoCreateBackupViewModel.State>
        field = MutableStateFlow<AnimatoCreateBackupViewModel.State>(State())

    fun toggle(setter: (BackupOptions, Boolean) -> BackupOptions, enabled: Boolean) {
        state.update { it.copy(options = setter(it.options, enabled)) }
    }

    fun createBackup(context: Context, uri: Uri) {
        AnimatoBackupCreateJob.startNow(context, uri, state.value.options)
    }

    @Immutable
    data class State(val options: BackupOptions = BackupOptions())
}
