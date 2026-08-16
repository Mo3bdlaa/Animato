package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

// The settings sections that answer for both halves.
//
// ## The asymmetry these exist to end
//
// Anime's settings used to live inside a single top-level entry called "Anime", which held five
// screens: library, player, downloads, browse and tracking. So manga's library options sat one tap
// from the root while anime's sat three, and the list read as a manga app with an anime annex
// bolted to the side — which is exactly how it felt to use.
//
// Each anime screen now sits inside the section it belongs to. Someone looking for library options
// finds both libraries' options under Library, because that is what the word means.
//
// ## Why wrappers rather than edits
//
// These are Mihon's screens and we do not open Mihon's files. Each wrapper asks the real screen for
// its preference list, appends one group, and delegates the rest — the technique
// AnimatoSettingsDataScreen already uses for the backup group. Mihon's screens keep changing
// underneath us and keep working.
//
// They are one file rather than four because the four are the same eight lines with different
// nouns; split up, the shape would be harder to see than the difference.
//
// The player is deliberately not here. It is not a subsection of anything — it is the whole of
// "Watching", and a sibling of "Reading" at the root. See AnimatoSettingsMainScreen.

/** Mihon's library settings, plus the anime library's. */
object AnimatoSettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        return SettingsLibraryScreen.getPreferences() + Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.label_anime),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.label_anime_library),
                    onClick = { navigator.push(SettingsAnimeLibraryScreen) },
                ),
            ),
        )
    }
}

/** Mihon's download settings, plus the anime downloader's. */
object AnimatoSettingsDownloadsScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.pref_downloads_storage

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        return SettingsDownloadScreen.getPreferences() + Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.label_anime),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.label_downloaded),
                    onClick = { navigator.push(SettingsAnimeDownloadScreen) },
                ),
            ),
        )
    }
}

/** Mihon's tracking settings, plus the anime trackers'. */
object AnimatoSettingsTrackingScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        return SettingsTrackingScreen.getPreferences() + Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.label_anime),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_category_tracking),
                    onClick = { navigator.push(SettingsAnimeTrackingScreen) },
                ),
            ),
        )
    }
}

/**
 * Browsing, for both halves, and the Cloudflare screen that had nowhere else to be.
 *
 * *Unblock a source* was a top-level entry, which overstated it: it is a repair for one thing that
 * goes wrong with sources, not a peer of Library and Tracking. It belongs where sources are.
 */
object AnimatoSettingsSourcesScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.pref_category_sources

    @Composable
    override fun getPreferences(): List<Preference> {
        val navigator = LocalNavigator.currentOrThrow
        return SettingsBrowseScreen.getPreferences() + Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.label_anime),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.label_anime_sources),
                    onClick = { navigator.push(SettingsAnimeBrowseScreen) },
                ),
            ),
        ) + Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_cloudflare_title),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.pref_cloudflare_title),
                    subtitle = stringResource(AYMR.strings.pref_cloudflare_summary),
                    onClick = { navigator.push(AnimatoCloudflareScreen) },
                ),
            ),
        )
    }
}
