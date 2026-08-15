package animato.anime.track.shikimori

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriInterceptor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Shikimori, for anime.
 *
 * Delegates to Mihon's tracker for everything about being one — see [AnimeAnilist][
 * animato.anime.track.anilist.AnimeAnilist] for why.
 *
 * Shikimori uses the same status words for both kinds of media — "watching" means reading on a
 * manga — so the numbers here are the anime ones and the words are Shikimori's.
 */
class AnimeShikimori(private val delegate: Shikimori) :
    Tracker by delegate,
    AnimeTracker,
    DeletableAnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
        const val REWATCHING = 16L

        private val SCORE_LIST = IntRange(0, 10).map(Int::toString).toImmutableList()

        fun statusFrom(apiStatus: String): Long = when (apiStatus) {
            "watching" -> WATCHING
            "completed" -> COMPLETED
            "on_hold" -> ON_HOLD
            "dropped" -> DROPPED
            "planned" -> PLAN_TO_WATCH
            "rewatching" -> REWATCHING
            else -> throw NotImplementedError("Unknown Shikimori status: $apiStatus")
        }
    }

    private val interceptor by lazy { ShikimoriInterceptor(delegate) }

    private val api by lazy { AnimeShikimoriApi(delegate.id, delegate.client, interceptor) }

    override fun getCompletionStatus(): Long = COMPLETED

    override fun indexToScore(index: Int): Double = delegate.indexToScore(index)

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun getStatusListAnime(): List<Long> =
        listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH, REWATCHING)

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getStatusForAnime(status: Long) = when (status) {
        WATCHING -> AYMR.strings.watching
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        REWATCHING -> AYMR.strings.repeating_anime
        else -> null
    }

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toInt().toString()

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED && didWatchEpisode) {
            track.status = if (track.status == REWATCHING) REWATCHING else WATCHING
        }
        if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
            track.status = COMPLETED
            track.finished_watching_date = System.currentTimeMillis()
        }
        return api.updateLibAnime(track, userId())
    }

    override suspend fun delete(track: DomainAnimeTrack) = api.deleteLibAnime(track)

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.findLibAnime(track)
        return if (remoteTrack != null && remoteTrack.library_id != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRewatching = track.status == REWATCHING
                track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            api.addLibAnime(track, userId())
        }
    }

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = api.searchAnime(query)

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        // Null here means the entry was removed on Shikimori while still linked in the app, which
        // is worth failing on rather than silently keeping stale numbers.
        val remoteTrack = api.findLibAnime(track, isRefresh = true)
            ?: throw Exception("Could not find anime on Shikimori")
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    /**
     * Shikimori stores the user id where a password would go, which is Mihon's arrangement.
     */
    private fun userId(): String = delegate.getPassword()
}

/**
 * The name Shikimori knows this status by.
 */
fun AnimeTrack.toShikimoriStatus(): String = when (status) {
    AnimeShikimori.WATCHING -> "watching"
    AnimeShikimori.COMPLETED -> "completed"
    AnimeShikimori.ON_HOLD -> "on_hold"
    AnimeShikimori.DROPPED -> "dropped"
    AnimeShikimori.PLAN_TO_WATCH -> "planned"
    AnimeShikimori.REWATCHING -> "rewatching"
    else -> throw NotImplementedError("Unknown status: $status")
}
