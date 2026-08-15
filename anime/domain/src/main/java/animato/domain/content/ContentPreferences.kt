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
 * This is [ContentType] and not [ContentFilter] on purpose. The filter has an `ALL`, and `ALL` only
 * means something once a screen can draw both halves at once; until those screens exist, offering
 * it would be offering a setting that quietly does nothing.
 */
class ContentPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val contentType = preferenceStore.getEnum("animato_content_type", ContentType.MANGA)
}
