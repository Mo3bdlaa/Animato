package eu.kanade.tachiyomi.ui.entries.anime.track

import eu.kanade.tachiyomi.data.track.AnimeTracker
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * One row of the tracking sheet: a tracker, and the entry it holds for this anime if it has one.
 *
 * The tracker is an [AnimeTracker] rather than a `Tracker`, because a row is only ever built for
 * one that tracks anime. Typing it that way is what removes the cast every reader of this used to
 * need before it could ask a single anime question.
 */
data class AnimeTrackItem(val track: AnimeTrack?, val tracker: AnimeTracker)
