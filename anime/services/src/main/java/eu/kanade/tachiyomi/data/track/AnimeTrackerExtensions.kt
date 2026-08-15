package eu.kanade.tachiyomi.data.track

/**
 * The anime half of a tracker.
 *
 * Aniyomi added an abstract `animeService: AnimeTracker` property to Mihon's own [Tracker]
 * interface, so every tracker — including the manga-only ones — had to declare one. This asks the
 * same question from the outside: a tracker either implements [AnimeTracker] or it does not.
 */
val Tracker.animeService: AnimeTracker
    get() = this as? AnimeTracker
        ?: error("${this::class.simpleName} does not track anime")
