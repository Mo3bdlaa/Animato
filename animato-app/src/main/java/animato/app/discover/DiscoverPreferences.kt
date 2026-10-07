package animato.app.discover

import animato.anime.di.AnimatoScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

/**
 * Where Discover's front page comes from.
 *
 * ## The two answers
 *
 * - [DiscoverMode.GENERAL]: what the world is watching and reading — public metadata rails that need
 *   no source at all, with *Your sources* underneath. The default, because it is the one that works
 *   on a fresh install.
 * - [DiscoverMode.MY_SOURCES]: only what the installed sources offer, a section per catalogue. Asked
 *   for from a device by somebody whose sources *are* the point — a Stremio addon's own *Popular
 *   movies* and *Popular series* are a better front page for them than a chart of titles their
 *   sources may not carry, every one of which opens a search rather than the title.
 */
@SingleIn(AnimatoScope::class)
@Inject
class DiscoverPreferences(preferenceStore: PreferenceStore) {

    val mode = preferenceStore.getEnum("animato_discover_mode", DiscoverMode.GENERAL)
}

enum class DiscoverMode { GENERAL, MY_SOURCES }
