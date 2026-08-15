package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animato.anime.ui.category.visualName
import animato.domain.category.AnimeCategory
import aniyomi.domain.download.service.AnimeDownloadPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The anime half of the download settings.
 *
 * Everything here is about episodes and video, so nothing on it duplicates a manga control. The
 * shared settings — where downloads are stored, whether to download over Wi-Fi only — stay in
 * Mihon's screen, because they are one setting for the whole app rather than one per library.
 */
object SettingsAnimeDownloadScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.label_anime

    @Composable
    override fun getPreferences(): List<Preference> {
        val downloadPreferences = remember { Injekt.get<AnimeDownloadPreferences>() }
        val getCategories = remember { Injekt.get<GetAnimeCategories>() }
        val allCategories by getCategories.subscribe().collectAsStateWithLifecycle(emptyList())

        return listOf(
            getAutoDownloadGroup(downloadPreferences, allCategories),
            getDeleteEpisodesGroup(downloadPreferences, allCategories),
            getExternalDownloaderGroup(downloadPreferences),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: AnimeDownloadPreferences,
        allCategories: List<AnimeCategory>,
    ): Preference.PreferenceGroup {
        val downloadNewEpisodes by downloadPreferences.downloadNewEpisodes().collectAsState()
        val included by downloadPreferences.downloadNewEpisodeCategories().collectAsState()
        val excluded by downloadPreferences.downloadNewEpisodeCategoriesExclude().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadNewEpisodes(),
                    title = stringResource(AYMR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = downloadPreferences.downloadNewEpisodeCategories(),
                    entries = allCategories.associate { it.id.toString() to it.visualName }.toImmutableMap(),
                    title = stringResource(MR.strings.categories),
                    subtitle = categoriesLabel(allCategories, included, excluded),
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadNewUnseenEpisodesOnly(),
                    title = stringResource(AYMR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileWatching(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith { if (it == 0) stringResource(MR.strings.disabled) else it.toString() }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.auto_download_while_watching),
                ),
            ),
        )
    }

    @Composable
    private fun getDeleteEpisodesGroup(
        downloadPreferences: AnimeDownloadPreferences,
        allCategories: List<AnimeCategory>,
    ): Preference.PreferenceGroup {
        val excluded by downloadPreferences.removeExcludeAnimeCategories().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadFillermarkedItems(),
                    title = stringResource(AYMR.strings.pref_download_fillermarked_items),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = downloadPreferences.removeExcludeAnimeCategories(),
                    entries = allCategories.associate { it.id.toString() to it.visualName }.toImmutableMap(),
                    title = stringResource(MR.strings.pref_remove_exclude_categories),
                    subtitle = categoriesLabel(allCategories, emptySet(), excluded),
                ),
            ),
        )
    }

    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: AnimeDownloadPreferences,
    ): Preference.PreferenceGroup {
        val useExternal by downloadPreferences.useExternalDownloader().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_downloader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.useExternalDownloader(),
                    title = stringResource(AYMR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = downloadPreferences.externalDownloaderSelection(),
                    title = stringResource(AYMR.strings.pref_external_downloader_selection),
                    enabled = useExternal,
                ),
            ),
        )
    }
}
