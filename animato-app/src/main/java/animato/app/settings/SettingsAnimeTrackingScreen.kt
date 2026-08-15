package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import aniyomi.domain.track.service.AnimeTrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The two tracking settings that are not Mihon's.
 *
 * Signing in to a tracker, and everything about which tracker updates when, is shared and stays in
 * Mihon's tracking screen. Only these two are anime's: one because Mihon has no airing times to
 * show, and one because Mihon simply does not offer it.
 *
 * A short screen, honestly. It grows when the anime-only trackers are ported.
 */
object SettingsAnimeTrackingScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun getPreferences(): List<Preference> {
        val trackPreferences = remember { Injekt.get<AnimeTrackPreferences>() }

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
                ),
            ),
        )
    }
}
