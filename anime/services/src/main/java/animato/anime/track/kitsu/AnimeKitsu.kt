package animato.anime.track.kitsu

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.kitsu.KitsuInterceptor
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.text.DecimalFormat
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Kitsu, for anime.
 *
 * Delegates to Mihon's tracker for everything about being one — see [AnimeAnilist][
 * animato.anime.track.anilist.AnimeAnilist] for why.
 *
 * Kitsu has no rewatching status, so there is no number for one here and the tracking sheet does
 * not offer it. The status numbers are otherwise Aniyomi's, because they go into every backup.
 */
class AnimeKitsu(private val delegate: Kitsu) : Tracker by delegate, AnimeTracker, DeletableAnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L

        private const val BASE_ANIME_URL = "https://kitsu.app/anime/"

        fun animeUrl(remoteId: Long): String = BASE_ANIME_URL + remoteId
    }

    private val interceptor by lazy { KitsuInterceptor(delegate) }

    private val api by lazy { AnimeKitsuApi(delegate.client, interceptor) }

    override fun getCompletionStatus(): Long = COMPLETED

    /**
     * Kitsu scores in halves from 0.5 to 10, so the list is "0" and then every half step.
     */
    override fun getScoreList(): ImmutableList<String> {
        val format = DecimalFormat("0.#")
        return (listOf("0") + IntRange(2, 20).map { format.format(it / 2f) }).toImmutableList()
    }

    override fun indexToScore(index: Int): Double = delegate.indexToScore(index)

    override fun getStatusListAnime(): List<Long> =
        listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH)

    override fun getWatchingStatus(): Long = WATCHING

    /**
     * Kitsu has no rewatching status. Returning watching keeps the "started again" path a no-op
     * rather than writing a status Kitsu would reject.
     */
    override fun getRewatchingStatus(): Long = WATCHING

    override fun getStatusForAnime(status: Long) = when (status) {
        WATCHING -> AYMR.strings.watching
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        else -> null
    }

    override fun displayScore(track: DomainAnimeTrack): String = DecimalFormat("0.#").format(track.score)

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED && didWatchEpisode) {
            track.status = WATCHING
        }
        if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
            track.status = COMPLETED
            track.finished_watching_date = System.currentTimeMillis()
        }
        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainAnimeTrack) = api.removeLibAnime(track)

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.findLibAnime(track, userId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
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
        val remoteTrack = api.getLibAnime(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    /**
     * Kitsu stores the user id where a password would go, which is Mihon's arrangement and not one
     * to change: the same value has to mean the same thing to both halves.
     */
    private fun userId(): String = delegate.getPassword()
}

/**
 * The name Kitsu knows this status by.
 */
fun AnimeTrack.toApiStatus(): String = when (status) {
    AnimeKitsu.WATCHING -> "current"
    AnimeKitsu.COMPLETED -> "completed"
    AnimeKitsu.ON_HOLD -> "on_hold"
    AnimeKitsu.DROPPED -> "dropped"
    AnimeKitsu.PLAN_TO_WATCH -> "planned"
    else -> throw Exception("Unknown Kitsu status: $status")
}

/**
 * Kitsu scores out of twenty, so a score of ten goes out as twenty. Null clears it.
 */
fun AnimeTrack.toApiScore(): String? = if (score > 0) (score * 2).toInt().toString() else null
