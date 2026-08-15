package animato.anime.track.anilist

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack

/**
 * The name AniList knows this status by.
 *
 * AniList has one set of list statuses for both kinds of media — "CURRENT" is reading or watching
 * depending on what the entry is — so the mapping is only from our anime numbers to their words.
 */
fun AnimeTrack.toApiStatus(): String = when (status) {
    AnimeAnilist.WATCHING -> "CURRENT"
    AnimeAnilist.COMPLETED -> "COMPLETED"
    AnimeAnilist.ON_HOLD -> "PAUSED"
    AnimeAnilist.DROPPED -> "DROPPED"
    AnimeAnilist.PLAN_TO_WATCH -> "PLANNING"
    AnimeAnilist.REWATCHING -> "REPEATING"
    else -> throw NotImplementedError("Unknown AniList status: $status")
}
