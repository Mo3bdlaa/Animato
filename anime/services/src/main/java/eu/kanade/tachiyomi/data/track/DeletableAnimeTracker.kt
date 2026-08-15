package eu.kanade.tachiyomi.data.track

import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * A tracker that can remove an entry from the user's list, not just update it.
 *
 * Separate from [AnimeTracker] because not every service supports it — the tracking screen offers
 * "remove from list" only when the tracker implements this, rather than offering it everywhere and
 * failing on the ones that cannot.
 */
interface DeletableAnimeTracker {

    suspend fun delete(track: AnimeTrack)
}
