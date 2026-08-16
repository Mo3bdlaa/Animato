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
import animato.anime.player.settings.PlayerSettingsMainScreen
import animato.app.updater.AnimatoAppUpdateChecker
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSearchScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
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

    /**
     * Ten entries, organised by what someone is doing rather than by which half they are in.
     *
     * The old list was Mihon's with anime bolted on: a *Reader* entry at the top level and a
     * *Player* buried three taps down inside a bucket called *Anime*, plus a stray *Unblock a
     * source* sitting as a peer of Library. Reading and Watching are siblings here, which is the
     * whole point — neither medium is the main one — and each shared section now answers for both
     * halves rather than one, through the wrappers in AnimatoSettingsSections.kt.
     */
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
            screen = AnimatoSettingsLibraryScreen,
        ),
        // Reading and Watching, adjacent and equal. The pair is the fix: whichever medium someone
        // came for, the other one is visibly right there rather than nested somewhere else.
        Item(
            titleRes = AYMR.strings.pref_category_reading,
            subtitleRes = AYMR.strings.pref_reading_summary,
            icon = Icons.Outlined.ChromeReaderMode,
            screen = SettingsReaderScreen,
        ),
        Item(
            titleRes = AYMR.strings.pref_category_watching,
            subtitleRes = AYMR.strings.pref_watching_summary,
            icon = Icons.Outlined.PlayCircleOutline,
            screen = PlayerSettingsMainScreen(mainSettings = false),
        ),
        Item(
            titleRes = AYMR.strings.pref_category_sources,
            subtitleRes = AYMR.strings.pref_sources_summary,
            icon = Icons.Outlined.Explore,
            screen = AnimatoSettingsSourcesScreen,
        ),
        Item(
            titleRes = AYMR.strings.pref_downloads_storage,
            subtitleRes = AYMR.strings.pref_downloads_storage_summary,
            icon = Icons.Outlined.GetApp,
            screen = AnimatoSettingsDownloadsScreen,
        ),
        Item(
            titleRes = MR.strings.pref_category_tracking,
            subtitleRes = MR.strings.pref_tracking_summary,
            icon = Icons.Outlined.Sync,
            screen = AnimatoSettingsTrackingScreen,
        ),
        Item(
            titleRes = AYMR.strings.pref_backup_data,
            subtitleRes = AYMR.strings.pref_backup_data_summary,
            icon = Icons.Outlined.Storage,
            screen = AnimatoSettingsDataScreen,
        ),
        Item(
            titleRes = AYMR.strings.pref_privacy_security,
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
