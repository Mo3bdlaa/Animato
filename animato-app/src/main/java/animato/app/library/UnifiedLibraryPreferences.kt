package animato.app.library

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * How the library is sorted, filtered and drawn — remembered between launches.
 *
 * Everything the filter sheet changes lives here rather than in the screen model's state, because
 * all of it is a preference in the ordinary sense: someone who wants four columns and unread-only
 * wants that tomorrow too. Nothing here narrows what is *in* the library, so nothing here is
 * dangerous to remember — the lens, which does narrow it, is deliberately elsewhere and shows
 * itself in the top bar.
 *
 * The one thing that is not remembered is the selected category chip. A category is a place you
 * walked to, not a setting, and reopening the app inside a shelf you cannot see the edge of is the
 * hidden-state problem the lens button exists to avoid.
 */
class UnifiedLibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val sortMode = preferenceStore.getEnum("animato_library_sort", LibrarySortMode.RECENTLY_UPDATED)

    val unviewedOnly = preferenceStore.getBoolean("animato_library_filter_unviewed", false)

    val downloadedOnly = preferenceStore.getBoolean("animato_library_filter_downloaded", false)

    val trackedOnly = preferenceStore.getBoolean("animato_library_filter_tracked", false)

    /**
     * Covers per row.
     *
     * Three is the default because the grid is designed at three: 16 + 3×112 + 2×12 + 16 comes to
     * exactly 393 dp, the artboard width. Two and four are offered and work, but three is the size
     * the cover art was measured for.
     */
    val columns = preferenceStore.getInt("animato_library_columns", DEFAULT_COLUMNS)

    val showUnviewedCount = preferenceStore.getBoolean("animato_library_unviewed_badge", true)

    companion object {
        const val DEFAULT_COLUMNS = 3
        val COLUMN_CHOICES = listOf(2, 3, 4)
    }
}
