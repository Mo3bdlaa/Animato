package animato.anime.track.anilist

import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.anilist.AnilistInterceptor
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * AniList, for anime.
 *
 * Mihon's AniList tracker is delegated to rather than copied or subclassed, which is what makes
 * this small. Everything about *being* a tracker — the id, the logo, the credentials, logging in,
 * the OAuth token and its refresh — is already right in Mihon's, and none of it differs by media
 * type. What differs is the queries, and those are in [AnimeAnilistApi].
 *
 * The practical consequence: a user signs in once, on Mihon's own tracking screen, and both halves
 * work. There is no second login, no second token, and no way for the two to disagree about whether
 * they are signed in.
 *
 * The status numbers are Aniyomi's, deliberately. They are written into the database and into every
 * backup, so choosing different ones would mean an Aniyomi backup restoring anime with the wrong
 * status — silently, since every number is a valid status.
 */
class AnimeAnilist(private val delegate: Anilist) : Tracker by delegate, AnimeTracker, DeletableAnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 15L
        const val REWATCHING = 16L
    }

    private val trackPreferences: TrackPreferences by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(delegate, delegate.getPassword()) }

    private val api by lazy { AnimeAnilistApi(delegate.client, interceptor) }

    private val scorePreference = trackPreferences.anilistScoreType

    // Both interfaces declare these, so which one is meant has to be said. In every case the answer
    // is the delegate's: a score format is an account setting, not a per-media-type one.
    override fun getCompletionStatus(): Long = COMPLETED

    override fun indexToScore(index: Int): Double = delegate.indexToScore(index)

    override fun getScoreList(): ImmutableList<String> = when (scorePreference.get()) {
        Anilist.POINT_10 -> IntRange(0, 10).map(Int::toString).toImmutableList()
        Anilist.POINT_100 -> IntRange(0, 100).map(Int::toString).toImmutableList()
        Anilist.POINT_5 -> IntRange(0, 5).map { "$it ★" }.toImmutableList()
        Anilist.POINT_3 -> persistentListOf("-", "😦", "😐", "😊")
        Anilist.POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }.toImmutableList()
        else -> persistentListOf()
    }

    override fun getStatusListAnime(): List<Long> =
        listOf(WATCHING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_WATCH, REWATCHING)

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = REWATCHING

    override fun getStatusForAnime(status: Long) = when (status) {
        WATCHING -> AYMR.strings.watching
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        DROPPED -> MR.strings.dropped
        REWATCHING -> AYMR.strings.repeating_anime
        else -> null
    }

    override fun displayScore(track: DomainAnimeTrack): String {
        val score = track.score
        return when (scorePreference.get()) {
            Anilist.POINT_5 -> if (score == 0.0) "0 ★" else "${((score + 10) / 20).toInt()} ★"
            Anilist.POINT_3 -> when {
                score == 0.0 -> "0"
                score <= 35 -> "😦"
                score <= 60 -> "😐"
                else -> "😊"
            }
            else -> track.toApiScore()
        }
    }

    private suspend fun add(track: AnimeTrack): AnimeTrack = api.addLibAnime(track)

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        // Finishing the last episode moves the entry to completed rather than leaving it watching,
        // which is what the user would have done by hand a moment later.
        if (track.total_episodes != 0L && track.last_episode_seen.toLong() == track.total_episodes) {
            track.status = COMPLETED
            track.finished_watching_date = System.currentTimeMillis()
        } else if (didWatchEpisode) {
            if (track.status != REWATCHING) track.status = WATCHING
        }
        return api.updateLibAnime(track)
    }

    override suspend fun delete(track: DomainAnimeTrack) = api.deleteLibAnime(track)

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.findLibAnime(track, userId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRewatching = track.status == REWATCHING
                track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            // Not on the user's list yet, so this is the first thing they will see about it.
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = api.searchAnime(query)

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        val remoteTrack = api.getLibAnime(track, userId())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    /**
     * AniList identifies a user by a number, and Mihon stores it as the username.
     */
    private fun userId(): Int = delegate.getUsername().toInt()

    private fun DomainAnimeTrack.toApiScore(): String = when (scorePreference.get()) {
        Anilist.POINT_10 -> (score.toInt() / 10).toString()
        Anilist.POINT_10_DECIMAL -> (score / 10).toString()
        else -> score.toInt().toString()
    }
}
