package animato.app.downloads

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Whether downloads are cleaned up after their entry leaves the library.
 *
 * Its own class rather than a line in Mihon's `DownloadPreferences`, which is a file we do not edit,
 * and rather than a line in the anime download preferences, which this is not — the sweep covers
 * both halves and belongs to neither.
 */
class DownloadCleanupPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * On by default, which is the unusual choice here and a deliberate one.
     *
     * A setting that deletes files normally starts off. This one starts on because the state it
     * corrects is not a preference someone expressed — nobody decided to keep episodes of a show
     * they removed from their library; the files stayed because nothing ever removed them. Leaving
     * it off by default would mean the storage still fills up for everyone who never opens
     * settings, which is almost everyone.
     */
    fun deleteWhenRemovedFromLibrary() = preferenceStore.getBoolean(
        "pref_delete_downloads_not_in_library",
        true,
    )
}
