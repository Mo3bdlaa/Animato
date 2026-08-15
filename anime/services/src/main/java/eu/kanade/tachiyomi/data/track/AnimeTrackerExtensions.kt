package eu.kanade.tachiyomi.data.track

import animato.anime.track.AnimeTrackerManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The anime half of a tracker.
 *
 * Aniyomi added an abstract `animeService: AnimeTracker` property to Mihon's own [Tracker]
 * interface, so every tracker — including the manga-only ones — had to declare one. This asks the
 * same question from the outside.
 *
 * It cannot be a cast, which is what it was until the anime trackers existed. Mihon's trackers are
 * Mihon's, and none of them implements [AnimeTracker]; the anime half is a separate object that
 * shares the tracker's id and credentials, and [AnimeTrackerManager] is what holds the pairing. So
 * the question "what is the anime half of this tracker" is answered by looking it up rather than by
 * asking the object about itself.
 */
val Tracker.animeService: AnimeTracker
    get() = animeServiceOrNull ?: error("$name does not track anime")

/**
 * The anime half, or null when this tracker has none.
 *
 * For the places deciding whether to offer something, rather than the ones about to use it.
 */
val Tracker.animeServiceOrNull: AnimeTracker?
    get() = Injekt.get<AnimeTrackerManager>().get(id)
