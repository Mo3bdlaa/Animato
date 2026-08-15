package aniyomi.domain.source.service

import aniyomi.domain.source.interactor.SetAnimeMigrateSorting
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Source preferences that only the anime half reads or writes.
 *
 * Aniyomi added these to Mihon's own [eu.kanade.domain.source.service.SourcePreferences], next to
 * the manga ones. Keeping them here means the anime extension code no longer reaches into a class
 * upstream owns, and the count of pending anime extension updates stops sharing a file with the
 * count of manga ones.
 *
 * Preference keys are unchanged, so existing installs keep their state.
 *
 * These are properties rather than functions because several of their names — migrationSortingMode
 * and migrationSortingDirection — also exist on Mihon's SourcePreferences, and a name that means
 * the same thing on both sides should not be called two different ways depending on which one you
 * happen to have. The rest of our preference classes are still functions; see
 * UPSTREAM_DIVERGENCE.md.
 */
class AnimeSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    val animeExtensionUpdatesCount = preferenceStore.getInt("animeext_updates_count", 0)

    val incognitoAnimeExtensions = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())

    val disabledAnimeSources = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())

    val pinnedAnimeSources = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())

    val lastUsedAnimeSource = preferenceStore.getLong("last_anime_catalogue_source", -1)

    val hideInAnimeLibraryItems = preferenceStore.getBoolean("browse_hide_in_anime_library_items", false)

    /**
     * Sorting for the anime migration source list.
     *
     * Mihon keeps its own under the same names for manga. These are separate keys rather than shared
     * ones because the two lists are sorted independently — a user who orders manga sources by
     * favourite count has said nothing about how they want anime sources ordered.
     */
    val migrationSortingMode = preferenceStore.getEnum(
        "animelib_migration_sorting_mode",
        SetAnimeMigrateSorting.Mode.ALPHABETICAL,
    )

    val migrationSortingDirection = preferenceStore.getEnum(
        "animelib_migration_sorting_direction",
        SetAnimeMigrateSorting.Direction.ASCENDING,
    )
}
