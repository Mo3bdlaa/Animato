package aniyomi.domain.library.service

import aniyomi.domain.anime.SeasonDisplayMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * Preferences that only the anime half of the app reads or writes.
 *
 * These used to live alongside the manga ones in [tachiyomi.domain.library.service.LibraryPreferences], which meant
 * every anime feature reached into a class shared with upstream code. Keeping them apart lets the anime side move
 * into its own module without dragging the manga preferences along, and lets the shared class stay close to upstream.
 *
 * Preference keys are unchanged, so existing installs and backups keep their settings.
 */
class AnimeLibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun animeSortingMode() = preferenceStore.getObjectFromString(
        "animelib_sorting_mode",
        AnimeLibrarySort.default,
        AnimeLibrarySort.Serializer::serialize,
        AnimeLibrarySort.Serializer::deserialize,
    )

    fun randomAnimeSortSeed() = preferenceStore.getInt("library_random_anime_sort_seed", 0)

    // Columns

    fun animePortraitColumns() = preferenceStore.getInt("pref_animelib_columns_portrait_key", 0)

    fun animeLandscapeColumns() = preferenceStore.getInt("pref_animelib_columns_landscape_key", 0)

    // Filters

    fun filterDownloadedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_downloaded_v2", TriState.DISABLED)

    fun filterUnseen() =
        preferenceStore.getEnum("pref_filter_animelib_unread_v2", TriState.DISABLED)

    fun filterStartedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_started_v2", TriState.DISABLED)

    fun filterBookmarkedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_bookmarked_v2", TriState.DISABLED)

    fun filterCompletedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_completed_v2", TriState.DISABLED)

    fun filterTrackedAnime(id: Int) =
        preferenceStore.getEnum("pref_filter_animelib_tracked_${id}_v2", TriState.DISABLED)

    // Update count

    fun newAnimeUpdatesCount() = preferenceStore.getInt("library_unseen_updates_count", 0)

    // Categories

    fun defaultAnimeCategory() = preferenceStore.getInt(DEFAULT_ANIME_CATEGORY_PREF_KEY, -1)

    fun lastUsedAnimeCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_anime_category"), 0)

    fun animeUpdateCategories() =
        preferenceStore.getStringSet(LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY, emptySet())

    fun animeUpdateCategoriesExclude() =
        preferenceStore.getStringSet(LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY, emptySet())

    // Episodes

    fun filterEpisodeBySeen() =
        preferenceStore.getLong("default_episode_filter_by_seen", Anime.SHOW_ALL)

    fun filterEpisodeByDownloaded() =
        preferenceStore.getLong("default_episode_filter_by_downloaded", Anime.SHOW_ALL)

    fun filterEpisodeByBookmarked() =
        preferenceStore.getLong("default_episode_filter_by_bookmarked", Anime.SHOW_ALL)

    fun filterEpisodeByFillermarked() =
        preferenceStore.getLong("default_episode_filter_by_fillermarked", Anime.SHOW_ALL)

    // and upload date
    fun sortEpisodeBySourceOrNumber() = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    // The two preferences below share their keys with the manga side, so changing an episode default also changes
    // the chapter one. That is how it has always behaved; giving them their own keys here would silently reset
    // everyone's saved settings, so the keys stay as they are and the bug is fixed separately with a migration.
    fun displayEpisodeByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    fun sortEpisodeByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    fun showEpisodeThumbnailPreviews() = preferenceStore.getLong(
        "default_episode_show_thumbnail_previews",
        Anime.EPISODE_SHOW_PREVIEWS,
    )

    fun showEpisodeSummaries() = preferenceStore.getLong(
        "default_episode_show_summaries",
        Anime.EPISODE_SHOW_SUMMARIES,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen().set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded().set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked().set(anime.bookmarkedFilterRaw)
        filterEpisodeByFillermarked().set(anime.fillermarkedFilterRaw)
        sortEpisodeBySourceOrNumber().set(anime.sorting)
        displayEpisodeByNameOrNumber().set(anime.displayMode)
        sortEpisodeByAscendingOrDescending().set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
        showEpisodeThumbnailPreviews().set(anime.showPreviewsRaw)
        showEpisodeSummaries().set(anime.showSummariesRaw)
    }

    // Seasons

    fun filterSeasonByDownload() =
        preferenceStore.getLong("default_season_filter_by_downloaded", Anime.SHOW_ALL)

    fun filterSeasonByUnseen() =
        preferenceStore.getLong("default_season_filter_by_unseen", Anime.SHOW_ALL)

    fun filterSeasonByStarted() =
        preferenceStore.getLong("default_season_filter_by_started", Anime.SHOW_ALL)

    fun filterSeasonByCompleted() =
        preferenceStore.getLong("default_season_filter_by_completed", Anime.SHOW_ALL)

    fun filterSeasonByBookmarked() =
        preferenceStore.getLong("default_season_filter_by_bookmarked", Anime.SHOW_ALL)

    fun filterSeasonByFillermarked() =
        preferenceStore.getLong("default_season_filter_by_fillermarked", Anime.SHOW_ALL)

    fun sortSeasonBySourceOrNumber() = preferenceStore.getLong(
        "default_season_sort_by_source_or_number",
        Anime.SEASON_SORT_SOURCE,
    )

    fun sortSeasonByAscendingOrDescending() = preferenceStore.getLong(
        "default_season_sort_by_ascending_or_descending",
        Anime.SEASON_SORT_DESC,
    )

    fun seasonDisplayGridMode() = preferenceStore.getLong(
        "default_season_grid_display_mode",
        SeasonDisplayMode.toLong(SeasonDisplayMode.CompactGrid),
    )

    fun seasonDisplayGridSize() = preferenceStore.getInt(
        "default_season_grid_display_size",
        0,
    )

    fun seasonDownloadOverlay() = preferenceStore.getBoolean(
        "default_season_download_overlay",
        false,
    )

    fun seasonUnseenOverlay() = preferenceStore.getBoolean(
        "default_season_unseen_overlay",
        true,
    )

    fun seasonLocalOverlay() = preferenceStore.getBoolean(
        "default_season_local_overlay",
        true,
    )

    fun seasonLangOverlay() = preferenceStore.getBoolean(
        "default_season_lang_overlay",
        false,
    )

    fun seasonContinueOverlay() = preferenceStore.getBoolean(
        "default_season_continue_overlay",
        true,
    )

    fun seasonDisplayMode() = preferenceStore.getLong(
        "default_season_display_mode",
        Anime.SEASON_DISPLAY_MODE_SOURCE,
    )

    fun setSeasonSettingsDefault(anime: Anime) {
        filterSeasonByDownload().set(anime.seasonUnseenFilterRaw)
        filterSeasonByUnseen().set(anime.seasonUnseenFilterRaw)
        filterSeasonByStarted().set(anime.seasonStartedFilterRaw)
        filterSeasonByCompleted().set(anime.seasonCompletedFilterRaw)
        filterSeasonByBookmarked().set(anime.seasonBookmarkedFilterRaw)
        filterSeasonByFillermarked().set(anime.seasonFillermarkedFilterRaw)
        sortSeasonBySourceOrNumber().set(anime.seasonSorting)
        sortSeasonByAscendingOrDescending().set(
            if (anime.seasonSortDescending()) Anime.SEASON_SORT_DESC else Anime.SEASON_SORT_ASC,
        )
        seasonDisplayGridMode().set(SeasonDisplayMode.toLong(anime.seasonDisplayGridMode))
        seasonDisplayGridSize().set(anime.seasonDisplayGridSize)
        seasonDownloadOverlay().set(anime.seasonDownloadedOverlay)
        seasonUnseenOverlay().set(anime.seasonUnseenOverlay)
        seasonLocalOverlay().set(anime.seasonLocalOverlay)
        seasonLangOverlay().set(anime.seasonLangOverlay)
        seasonContinueOverlay().set(anime.seasonContinueOverlay)
        seasonDisplayMode().set(anime.seasonDisplayMode)
    }

    // Season behavior

    fun updateSeasonOnRefresh() =
        preferenceStore.getBoolean("pref_update_season_on_refresh", false)

    fun updateSeasonOnLibraryUpdate() =
        preferenceStore.getBoolean("pref_update_season_on_library_update", false)

    // Episode behavior

    fun swipeEpisodeStartAction() =
        preferenceStore.getEnum("pref_episode_swipe_end_action", EpisodeSwipeAction.ToggleSeen)

    fun swipeEpisodeEndAction() = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    fun markDuplicateSeenEpisodeAsSeen() = preferenceStore.getStringSet("mark_duplicate_seen_episode_seen", emptySet())

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,
        ToggleFillermark,
        Download,
        Disabled,
    }

    companion object {
        const val MARK_DUPLICATE_EPISODE_SEEN_NEW = "new_episode"
        const val MARK_DUPLICATE_EPISODE_SEEN_EXISTING = "existing_episode"

        /*
         * The library-update restriction set is a single preference read by both halves — one key,
         * one stored set of strings — so these alias Mihon's constants instead of repeating the
         * literals. Aniyomi renamed Mihon's constants in place, MANGA_* to ENTRY_*, which is why
         * they went missing the moment we stopped editing that file.
         *
         * Aliasing rather than copying makes drift impossible: if upstream changes a value, ours
         * changes with it and the two halves keep agreeing on what the preference means.
         */
        const val ANIME_NON_COMPLETED = LibraryPreferences.MANGA_NON_COMPLETED
        const val ANIME_HAS_UNSEEN = LibraryPreferences.MANGA_HAS_UNREAD
        const val ANIME_NON_SEEN = LibraryPreferences.MANGA_NON_READ
        const val ANIME_OUTSIDE_RELEASE_PERIOD = LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD

        const val DEFAULT_ANIME_CATEGORY_PREF_KEY = "default_anime_category"
        private const val LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY = "animelib_update_categories"
        private const val LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY = "animelib_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_ANIME_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_ANIME_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
