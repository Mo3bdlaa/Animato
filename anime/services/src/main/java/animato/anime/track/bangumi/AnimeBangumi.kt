package animato.anime.track.bangumi

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiInterceptor
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Bangumi, for anime.
 *
 * The only one of these that keeps Mihon's status numbers rather than declaring anime ones. That is
 * not a shortcut: on Bangumi the numbers *are* the API's own collection types, the same five for
 * every kind of subject, and inventing separate ones would mean translating them back on every
 * request for no gain.
 *
 * Bangumi has no rewatching status at all, so [getRewatchingStatus] answers with a number that is
 * not a status — which is what makes "started watching again" leave the entry alone rather than
 * write something Bangumi would reject.
 */
class AnimeBangumi(private val delegate: Bangumi) : Tracker by delegate, AnimeTracker {

    companion object {
        private val SCORE_LIST = IntRange(0, 10).map(Int::toString).toImmutableList()

        /**
         * Not a status. See the class comment.
         */
        private const val NO_REWATCHING_STATUS = -1L
    }

    private val interceptor by lazy { BangumiInterceptor(delegate) }

    private val api by lazy { AnimeBangumiApi(delegate.id, delegate.client, interceptor) }

    override fun getCompletionStatus(): Long = Bangumi.COMPLETED

    override fun indexToScore(index: Int): Double = delegate.indexToScore(index)

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun getStatusListAnime(): List<Long> = listOf(
        Bangumi.READING,
        Bangumi.COMPLETED,
        Bangumi.ON_HOLD,
        Bangumi.DROPPED,
        Bangumi.PLAN_TO_READ,
    )

    override fun getWatchingStatus(): Long = Bangumi.READING

    override fun getRewatchingStatus(): Long = NO_REWATCHING_STATUS

    override fun getStatusForAnime(status: Long) = when (status) {
        Bangumi.READING -> AYMR.strings.watching
        Bangumi.COMPLETED -> MR.strings.completed
        Bangumi.ON_HOLD -> MR.strings.on_hold
        Bangumi.DROPPED -> MR.strings.dropped
        Bangumi.PLAN_TO_READ -> AYMR.strings.plan_to_watch
        else -> null
    }

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toInt().toString()

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != Bangumi.COMPLETED && didWatchEpisode) {
            track.status = Bangumi.READING
        }
        if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
            track.status = Bangumi.COMPLETED
            track.finished_watching_date = System.currentTimeMillis()
        }
        return api.updateLibAnime(track)
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.statusLibAnime(track, username())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != Bangumi.COMPLETED) {
                track.status = if (hasSeenEpisodes) Bangumi.READING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) Bangumi.READING else Bangumi.PLAN_TO_READ
            track.score = 0.0
            api.addLibAnime(track)
        }
    }

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = api.searchAnime(query)

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        return api.statusLibAnime(track, username()) ?: track
    }

    private fun username(): String = delegate.getUsername()
}

/**
 * The collection type Bangumi knows this status by, which is the number itself.
 */
fun AnimeTrack.toApiStatus(): Int = when (status) {
    Bangumi.PLAN_TO_READ -> 1
    Bangumi.COMPLETED -> 2
    Bangumi.READING -> 3
    Bangumi.ON_HOLD -> 4
    Bangumi.DROPPED -> 5
    else -> throw NotImplementedError("Unknown Bangumi status: $status")
}
