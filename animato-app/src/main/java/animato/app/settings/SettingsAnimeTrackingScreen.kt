package animato.app.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import animato.anime.track.AnimeTrackerManager
import animato.anime.track.simkl.SimklApi
import aniyomi.domain.track.service.AnimeTrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The tracking settings that are not Mihon's.
 *
 * Signing in to a tracker Mihon also has stays on Mihon's tracking screen, and has to: those five
 * share its account and its stored credentials, so there is one place to manage them and this is
 * not it. What is here is what Mihon has nowhere to put — two anime-only settings, and the two
 * trackers that exist only on this side.
 */
object SettingsAnimeTrackingScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<AnimeTrackPreferences>() }
        val trackerManager = remember { Injekt.get<AnimeTrackerManager>() }
        val sourceManager = remember { Injekt.get<AnimeSourceManager>() }

        var signingOutOf by remember { mutableStateOf<Tracker?>(null) }

        signingOutOf?.let { tracker ->
            SignOutDialog(tracker = tracker, onDismiss = { signingOutOf = null })
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_tracking),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.trackOnAddingToLibrary(),
                        title = stringResource(AYMR.strings.pref_track_on_adding_to_library),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.showNextEpisodeAiringTime(),
                        title = stringResource(AYMR.strings.pref_show_next_episode_airing_time),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = trackPreferences.syncProgressFromTracker(),
                        title = stringResource(AYMR.strings.pref_sync_progress_from_tracker),
                        subtitle = stringResource(AYMR.strings.pref_sync_progress_from_tracker_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.simkl,
                        // The default browser and not a tab inside the app: only a real browser can
                        // hand the redirect back to us, and the redirect is the entire exchange.
                        login = { context.openInBrowser(SimklApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { signingOutOf = trackerManager.simkl },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.jellyfin,
                        // Nothing to ask for: the server, the account and the key were all
                        // configured in the Jellyfin extension. This only records that it is on.
                        login = { trackerManager.jellyfin.loginNoop() },
                        logout = { signingOutOf = trackerManager.jellyfin },
                    ),
                    Preference.PreferenceItem.InfoPreference(enhancedInfo(trackerManager, sourceManager)),
                ),
            ),
        )
    }

    /**
     * What the enhanced group says, plus a note about any of them whose source is not installed.
     *
     * An enhanced tracker works only through its own extension — Jellyfin tracks a Jellyfin server
     * and nothing else — so signing in to one whose extension is missing does nothing, and saying
     * so is kinder than letting it look broken.
     */
    @Composable
    private fun enhancedInfo(
        trackerManager: AnimeTrackerManager,
        sourceManager: AnimeSourceManager,
    ): String {
        val installed = sourceManager.getAll().mapNotNull { it::class.qualifiedName }.toSet()
        val missing = trackerManager.trackers
            .filterIsInstance<EnhancedAnimeTracker>()
            .filterNot { tracker -> tracker.getAcceptedSources().any { it in installed } }
            .filterIsInstance<Tracker>()
            .map { it.name }

        val info = stringResource(MR.strings.enhanced_tracking_info)
        if (missing.isEmpty()) return info

        return info + "\n\n" + stringResource(
            MR.strings.enhanced_services_not_installed,
            missing.joinToString(),
        )
    }

    /**
     * Mihon asks before signing out, and its dialog is private to its own screen, so this asks the
     * same question again.
     */
    @Composable
    private fun SignOutDialog(tracker: Tracker, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(MR.strings.logout_title, tracker.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        tracker.logout()
                        onDismiss()
                    },
                ) {
                    Text(stringResource(MR.strings.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
