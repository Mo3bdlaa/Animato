package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import animato.anime.ui.AnimeCategoriesScreen
import animato.anime.ui.category.visualName
import animato.domain.category.AnimeCategory
import aniyomi.domain.library.service.AnimeLibraryPreferences
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The anime half of the library settings.
 *
 * Aniyomi put these inside Mihon's own `SettingsLibraryScreen`, which is why that file ended up
 * twice its upstream length. They are a screen of their own here, reached from the Anime section,
 * so Mihon's stays exactly as Mihon wrote it.
 */
object SettingsAnimeLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.label_anime_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetAnimeCategories>() }
        val allCategories by getCategories.subscribe().collectAsStateWithLifecycle(emptyList())
        val libraryPreferences = remember { Injekt.get<AnimeLibraryPreferences>() }

        return listOf(
            getCategoriesGroup(allCategories, libraryPreferences),
            getGlobalUpdateGroup(allCategories, libraryPreferences),
            getSeasonGroup(libraryPreferences),
            getEpisodeGroup(libraryPreferences),
        )
    }

    @Composable
    private fun getCategoriesGroup(
        allCategories: List<AnimeCategory>,
        libraryPreferences: AnimeLibraryPreferences,
    ): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val userCategoriesCount = allCategories.filterNot(AnimeCategory::isSystemCategory).size
        val defaultCategory by libraryPreferences.defaultAnimeCategory().collectAsState()
        val selectedCategory = allCategories.find { it.id == defaultCategory.toLong() }

        // For default category
        val ids = listOf(libraryPreferences.defaultAnimeCategory().defaultValue()) +
            allCategories.fastMap { it.id.toInt() }
        val labels = listOf(stringResource(MR.strings.default_category_summary)) +
            allCategories.fastMap { it.visualName }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.general_categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.anime_categories),
                    subtitle = pluralStringResource(
                        MR.plurals.num_categories,
                        count = userCategoriesCount,
                        userCategoriesCount,
                    ),
                    onClick = { navigator.push(AnimeCategoriesScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.defaultAnimeCategory(),
                    entries = ids.zip(labels).toMap().toImmutableMap(),
                    title = stringResource(MR.strings.default_category),
                    subtitle = selectedCategory?.visualName ?: stringResource(MR.strings.default_category_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.hideHiddenCategoriesSettings(),
                    title = stringResource(AYMR.strings.pref_category_hide_hidden),
                ),
            ),
        )
    }

    @Composable
    private fun getGlobalUpdateGroup(
        allCategories: List<AnimeCategory>,
        libraryPreferences: AnimeLibraryPreferences,
    ): Preference.PreferenceGroup {
        val included by libraryPreferences.animeUpdateCategories().collectAsState()
        val excluded by libraryPreferences.animeUpdateCategoriesExclude().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.animeUpdateCategories(),
                    entries = allCategories.associate { it.id.toString() to it.visualName }.toImmutableMap(),
                    title = stringResource(MR.strings.categories),
                    subtitle = categoriesLabel(allCategories, included, excluded),
                ),
                // The update restrictions — skip completed, skip unstarted, predict the next
                // release — are one stored set that both libraries read, as they were in Aniyomi.
                // They are set in Mihon's library settings and deliberately not repeated here: two
                // controls over one value is worse than either place owning it.
            ),
        )
    }

    @Composable
    private fun getSeasonGroup(
        libraryPreferences: AnimeLibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_library_season),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateSeasonOnRefresh(),
                    title = stringResource(AYMR.strings.pref_update_seasons_refresh),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateSeasonOnLibraryUpdate(),
                    title = stringResource(AYMR.strings.pref_update_seasons_update),
                ),
            ),
        )
    }

    @Composable
    private fun getEpisodeGroup(
        libraryPreferences: AnimeLibraryPreferences,
    ): Preference.PreferenceGroup {
        val swipeActions = persistentMapOf(
            AnimeLibraryPreferences.EpisodeSwipeAction.Disabled to stringResource(MR.strings.disabled),
            AnimeLibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                stringResource(AYMR.strings.action_bookmark_episode),
            AnimeLibraryPreferences.EpisodeSwipeAction.ToggleFillermark to
                stringResource(AYMR.strings.action_fillermark_episode),
            AnimeLibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                stringResource(AYMR.strings.action_mark_as_seen),
            AnimeLibraryPreferences.EpisodeSwipeAction.Download to stringResource(MR.strings.action_download),
        )

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_behavior_episode),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeStartAction(),
                    entries = swipeActions,
                    title = stringResource(AYMR.strings.pref_episode_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeEndAction(),
                    entries = swipeActions,
                    title = stringResource(AYMR.strings.pref_episode_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateSeenEpisodeAsSeen(),
                    entries = persistentMapOf(
                        AnimeLibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_EXISTING to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_existing),
                        AnimeLibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_NEW to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_new),
                    ),
                    title = stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen),
                ),
            ),
        )
    }
}
