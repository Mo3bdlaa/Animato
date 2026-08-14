package aniyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Download preferences that only the anime side reads or writes.
 *
 * Shared ones — the wifi restriction, the removal slots, the download slot count — stay in
 * [tachiyomi.domain.download.service.DownloadPreferences], which both halves use.
 *
 * Preference keys are unchanged, so existing installs and backups keep their settings.
 */
class AnimeDownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun useExternalDownloader() = preferenceStore.getBoolean("use_external_downloader", false)

    fun externalDownloaderSelection() = preferenceStore.getString(
        "external_downloader_selection",
        "",
    )

    fun autoDownloadWhileWatching() = preferenceStore.getInt("auto_download_while_watching", 0)

    fun downloadFillermarkedItems() = preferenceStore.getBoolean("pref_download_fillermarked", false)

    fun removeExcludeAnimeCategories() = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    fun downloadNewEpisodes() = preferenceStore.getBoolean("download_new_episode", false)

    fun downloadNewEpisodeCategories() = preferenceStore.getStringSet(
        DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    fun downloadNewEpisodeCategoriesExclude() = preferenceStore.getStringSet(
        DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    fun downloadNewUnseenEpisodesOnly() = preferenceStore.getBoolean("download_new_unread_episodes_only", false)

    companion object {
        private const val REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY = "remove_exclude_anime_categories"
        private const val DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY = "download_new_anime_categories"
        private const val DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_anime_categories_exclude"

        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_ANIME_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_ANIME_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_ANIME_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
