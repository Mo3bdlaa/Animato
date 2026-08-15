package aniyomi.domain.source.service

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Source preferences that only the anime half reads or writes.
 *
 * Aniyomi added these to Mihon's own [eu.kanade.domain.source.service.SourcePreferences], next to
 * the manga ones. Keeping them here means the anime extension code no longer reaches into a class
 * upstream owns, and the count of pending anime extension updates stops sharing a file with the
 * count of manga ones.
 *
 * Preference keys are unchanged, so existing installs keep their state.
 */
class AnimeSourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun animeExtensionUpdatesCount() = preferenceStore.getInt("animeext_updates_count", 0)

    fun incognitoAnimeExtensions() = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
}
