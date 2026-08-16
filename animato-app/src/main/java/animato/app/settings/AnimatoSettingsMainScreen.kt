package animato.app.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import animato.app.updater.AnimatoAppUpdateChecker
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.util.system.toast
import io.github.mo3bdlaa.animato.BuildConfig
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

/**
 * The settings list, with the anime section on it.
 *
 * Mihon's own is an object holding a private list, so a row cannot be added to it from here. This
 * is that list with one more entry, and it is deliberately the *only* thing copied: every screen it
 * points at is Mihon's, unedited, including the ones the anime section sits next to.
 *
 * When Mihon adds a settings section, this file has to notice. That is the whole maintenance cost
 * of the approach, and it is one list against Aniyomi's alternative, which was editing five of
 * Mihon's settings screens and making each of them a conflict on every update.
 */
object AnimatoSettingsMainScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    actions = {
                        AppBarActions(
                            listOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.push(SettingsSearchScreen()) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { contentPadding ->
            LazyColumn(contentPadding = contentPadding) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.titleRes.resourceId },
                ) { _, item ->
                    TextPreferenceWidget(
                        title = stringResource(item.titleRes),
                        subtitle = item.subtitleRes?.let { stringResource(it) },
                        icon = item.icon,
                        onPreferenceClick = { navigator.push(item.screen) },
                    )
                }

                item {
                    CheckForUpdatesRow()
                }
            }
        }
    }

    /**
     * Asking for an update, and being told what happened.
     *
     * Mihon has this on its About screen and hides it behind its own `updaterEnabled`, which is
     * false in this build — so until now there was no way to check by hand and, worse, no way to
     * see *why* a check had come to nothing. The automatic check on launch swallows everything into
     * a log nobody reads:
     *
     * ```kotlin
     * catch (e: Exception) { logcat(LogPriority.ERROR, e) }
     * ```
     *
     * That silence is how the updater managed to ship twice without ever running. A check that
     * reports "you are on the newest build" or the actual error is the difference between a bug
     * someone can describe and a bug that just looks like nothing happening.
     */
    @Composable
    private fun CheckForUpdatesRow() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        var checking by remember { mutableStateOf(false) }

        TextPreferenceWidget(
            title = stringResource(MR.strings.check_for_updates),
            subtitle = if (checking) {
                stringResource(AYMR.strings.updater_checking)
            } else {
                BuildConfig.VERSION_NAME
            },
            icon = Icons.Outlined.NewReleases,
            onPreferenceClick = {
                if (checking) return@TextPreferenceWidget

                if (!AnimatoAppUpdateChecker.isEnabled) {
                    context.toast(AYMR.strings.updater_disabled)
                    return@TextPreferenceWidget
                }

                checking = true
                scope.launch {
                    try {
                        val release = AnimatoAppUpdateChecker().checkForUpdate()
                        if (release == null) {
                            context.toast(
                                context.stringResource(
                                    AYMR.strings.updater_up_to_date,
                                    BuildConfig.VERSION_NAME,
                                ),
                            )
                        } else {
                            navigator.push(
                                NewUpdateScreen(
                                    versionName = release.version,
                                    changelogInfo = release.info,
                                    releaseLink = release.releaseLink,
                                    downloadLink = release.downloadLink,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                        // The message, not a generic failure: "Unable to resolve host" and
                        // "rate limit exceeded" are different problems with different fixes.
                        context.toast(
                            context.stringResource(
                                AYMR.strings.updater_failed,
                                e.message ?: e::class.simpleName.orEmpty(),
                            ),
                        )
                    } finally {
                        checking = false
                    }
                }
            },
        )
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource? = null,
        val icon: ImageVector,
        val screen: VoyagerScreen,
    )

    private val items = listOf(
        Item(
            titleRes = MR.strings.pref_category_appearance,
            subtitleRes = MR.strings.pref_appearance_summary,
            icon = Icons.Outlined.Palette,
            screen = SettingsAppearanceScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_library,
            subtitleRes = MR.strings.pref_library_summary,
            icon = Icons.Outlined.CollectionsBookmark,
            screen = SettingsLibraryScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_reader,
            subtitleRes = MR.strings.pref_reader_summary,
            icon = Icons.Outlined.ChromeReaderMode,
            screen = SettingsReaderScreen,
        ),
        // The one row that is not Mihon's. It sits after the reader because that is where someone
        // looking for "how the player behaves" will look next.
        Item(
            titleRes = AYMR.strings.label_anime,
            subtitleRes = AYMR.strings.pref_anime_summary,
            icon = Icons.Outlined.PlayCircleOutline,
            screen = SettingsAnimeScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_downloads,
            subtitleRes = MR.strings.pref_downloads_summary,
            icon = Icons.Outlined.GetApp,
            screen = SettingsDownloadScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_tracking,
            subtitleRes = MR.strings.pref_tracking_summary,
            icon = Icons.Outlined.Sync,
            screen = SettingsTrackingScreen,
        ),
        Item(
            titleRes = MR.strings.browse,
            subtitleRes = MR.strings.pref_browse_summary,
            icon = Icons.Outlined.Explore,
            screen = SettingsBrowseScreen,
        ),
        // Also not Mihon's, and next to Browse because that is where a source that will not load
        // sends someone looking. It covers both halves — Cloudflare blocks a *site*, and the anime
        // screens and the manga screens are not the same screens.
        Item(
            titleRes = AYMR.strings.pref_cloudflare_title,
            subtitleRes = AYMR.strings.pref_cloudflare_summary,
            icon = Icons.Outlined.Shield,
            screen = AnimatoCloudflareScreen,
        ),
        Item(
            titleRes = MR.strings.label_data_storage,
            subtitleRes = MR.strings.pref_backup_summary,
            icon = Icons.Outlined.Storage,
            // Mihon's screen with one group appended — see AnimatoSettingsDataScreen.
            screen = AnimatoSettingsDataScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_security,
            subtitleRes = MR.strings.pref_security_summary,
            icon = Icons.Outlined.Security,
            screen = SettingsSecurityScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_advanced,
            subtitleRes = MR.strings.pref_advanced_summary,
            icon = Icons.Outlined.Code,
            screen = SettingsAdvancedScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_about,
            icon = Icons.Outlined.Info,
            screen = AboutScreen,
        ),
    )
}
