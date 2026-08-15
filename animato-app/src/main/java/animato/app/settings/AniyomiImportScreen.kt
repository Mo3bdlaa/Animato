package animato.app.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import animato.anime.backup.AniyomiBackupValidator
import animato.anime.backup.restore.AniyomiBackupRestoreJob
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.components.SectionCard
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * What the user sees after picking an Aniyomi backup, before anything is written.
 *
 * The same shape as Mihon's restore screen, because the decision being made is the same one: what
 * is in this file, what will not survive the trip, and which parts to take. The file is read once
 * here to answer the first two questions, and again by the job to do the work.
 */
class AniyomiImportScreen(
    private val uri: String,
    /**
     * Whether this arrived from the Aniyomi import row rather than from Restore backup.
     *
     * The screen does the same thing either way — it reads whatever the file is. Only the title
     * differs, because someone restoring their own backup should not be told they are importing
     * from an app they may never have used.
     */
    private val isAniyomiImport: Boolean = true,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = viewModel<AniyomiImportViewModel>(
            factory = AniyomiImportViewModel.Factory,
            extras = CreationExtras { set(AniyomiImportViewModel.URI_KEY, uri) },
        )
        val state by viewModel.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = if (isAniyomiImport) {
                        stringResource(AYMR.strings.aniyomi_import)
                    } else {
                        stringResource(MR.strings.pref_restore_backup)
                    },
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumnWithAction(
                contentPadding = contentPadding,
                actionLabel = if (isAniyomiImport) {
                    stringResource(AYMR.strings.aniyomi_import_action)
                } else {
                    stringResource(MR.strings.action_restore)
                },
                actionEnabled = state.canImport && state.options.canRestore(),
                onClickAction = {
                    viewModel.startImport()
                    navigator.pop()
                },
            ) {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    item { WarningBanner(MR.strings.restore_miui_warning) }
                }

                if (state.canImport) {
                    item {
                        SectionCard {
                            Text(
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                text = stringResource(
                                    AYMR.strings.aniyomi_import_contents,
                                    state.animeCount,
                                    state.mangaCount,
                                ),
                            )
                        }
                    }
                    item {
                        SectionCard {
                            RestoreOptions.options.forEach { option ->
                                LabeledCheckbox(
                                    label = stringResource(option.label),
                                    checked = option.getter(state.options),
                                    onCheckedChange = { viewModel.toggle(option.setter, it) },
                                )
                            }
                        }
                    }
                }

                state.problem?.let { problemItem(it) }
            }
        }
    }

    private fun LazyListScope.problemItem(problem: ImportProblem) = item {
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                val message = buildAnnotatedString {
                    when (problem) {
                        is ImportProblem.Missing -> {
                            appendLine(stringResource(MR.strings.backup_restore_content_full))
                            if (problem.sources.isNotEmpty()) {
                                appendLine()
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.backup_restore_missing_sources))
                                }
                                problem.sources.joinTo(this, separator = "\n- ", prefix = "- ")
                            }
                            if (problem.trackers.isNotEmpty()) {
                                appendLine()
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.backup_restore_missing_trackers))
                                }
                                problem.trackers.joinTo(this, separator = "\n- ", prefix = "- ")
                            }
                        }
                        is ImportProblem.Unreadable -> {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                appendLine(stringResource(MR.strings.invalid_backup_file))
                            }
                            appendLine(problem.uri)
                            appendLine()
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                appendLine(stringResource(MR.strings.invalid_backup_file_error))
                            }
                            appendLine(problem.message)
                        }
                    }
                }

                SelectionContainer { Text(text = message) }
            }
        }
    }
}

class AniyomiImportViewModel(
    private val context: Context,
    private val uri: String,
) : ViewModel() {

    val state: StateFlow<AniyomiImportViewModel.State>
        field = MutableStateFlow<AniyomiImportViewModel.State>(State())

    init {
        validate()
    }

    fun toggle(setter: (RestoreOptions, Boolean) -> RestoreOptions, enabled: Boolean) {
        state.update { it.copy(options = setter(it.options, enabled)) }
    }

    fun startImport() {
        AniyomiBackupRestoreJob.start(context, uri.toUri(), state.value.options)
    }

    /**
     * Reads the file to see what is in it.
     *
     * Off the main thread, unlike the screen this is modelled on: a large backup is several
     * megabytes of gzip and the screen would otherwise open frozen.
     */
    private fun validate() = viewModelScope.launch {
        val results = try {
            withIOContext { AniyomiBackupValidator(context).validate(uri.toUri()) }
        } catch (e: Exception) {
            state.update {
                it.copy(
                    problem = ImportProblem.Unreadable(uri, e.message.orEmpty()),
                    canImport = false,
                )
            }
            return@launch
        }

        state.update {
            it.copy(
                // Missing sources are worth saying out loud and are not a reason to stop. The
                // entries still restore; they are unreadable until the extension is installed.
                problem = ImportProblem.Missing(results.missingSources, results.missingTrackers)
                    .takeIf { problem -> problem.sources.isNotEmpty() || problem.trackers.isNotEmpty() },
                canImport = true,
                animeCount = results.animeCount,
                mangaCount = results.mangaCount,
            )
        }
    }

    @Immutable
    data class State(
        val problem: ImportProblem? = null,
        val canImport: Boolean = false,
        val animeCount: Int = 0,
        val mangaCount: Int = 0,
        val options: RestoreOptions = RestoreOptions(),
    )

    companion object {
        val URI_KEY = CreationExtras.Key<String>()

        val Factory = viewModelFactory {
            initializer {
                AniyomiImportViewModel(
                    context = Injekt.get<Application>(),
                    uri = get(URI_KEY)!!,
                )
            }
        }
    }
}

sealed interface ImportProblem {
    data class Missing(val sources: List<String>, val trackers: List<String>) : ImportProblem
    data class Unreadable(val uri: String, val message: String) : ImportProblem
}
