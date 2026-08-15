package animato.anime.track.simkl

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack

/**
 * The name Simkl knows this status by.
 */
fun AnimeTrack.toSimklStatus(): String = when (status) {
    AnimeSimkl.WATCHING -> "watching"
    AnimeSimkl.COMPLETED -> "completed"
    AnimeSimkl.ON_HOLD -> "hold"
    AnimeSimkl.NOT_INTERESTING -> "notinteresting"
    AnimeSimkl.PLAN_TO_WATCH -> "plantowatch"
    else -> throw NotImplementedError("Unknown Simkl status: $status")
}

/**
 * The status Simkl's name means here.
 *
 * "dropped" and "notinteresting" both arrive from Simkl and both land on the same status: the site
 * shows one list and the API has two names for it.
 */
fun toTrackStatus(status: String): Long = when (status) {
    "watching" -> AnimeSimkl.WATCHING
    "completed" -> AnimeSimkl.COMPLETED
    "hold" -> AnimeSimkl.ON_HOLD
    "dropped", "notinteresting" -> AnimeSimkl.NOT_INTERESTING
    "plantowatch" -> AnimeSimkl.PLAN_TO_WATCH
    else -> throw NotImplementedError("Unknown Simkl status: $status")
}
