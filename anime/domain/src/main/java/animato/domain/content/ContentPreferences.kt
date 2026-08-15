package animato.domain.content

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Which half of the library the user is currently looking at.
 *
 * Animato has one set of destinations rather than one per content type, so "am I looking at anime
 * or manga" is a lens over the whole app rather than a place in it. Library, Discover, Updates and
 * Downloads all read this, which is why it is one stored value and not four screen states: switch
 * on one screen and the others agree with you when you get there.
 *
 * It is persisted because it is a preference in the ordinary sense — someone who only watches anime
 * should not have to say so again every launch.
 *
 * [contentType] is a [ContentType] and not a [ContentFilter] on purpose: `ALL` only means something
 * on a screen that can draw both halves at once, and those destinations cannot. The library can,
 * so it has its own setting that does have one.
 */
class ContentPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val contentType = preferenceStore.getEnum("animato_content_type", ContentType.MANGA)

    /**
     * What the library destination shows.
     *
     * Separate from [contentType], and a [ContentFilter] rather than a [ContentType], because the
     * library is the one destination with a screen that can draw both halves at once. It defaults
     * to `ALL`, since one library is the point.
     */
    val libraryFilter = preferenceStore.getEnum("animato_library_filter", ContentFilter.ALL)
}
