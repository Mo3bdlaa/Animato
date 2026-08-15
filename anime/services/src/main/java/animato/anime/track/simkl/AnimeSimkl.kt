package animato.anime.track.simkl

import animato.anime.services.R
import animato.anime.track.AnimeOnlyTracker
import animato.anime.track.AnimeTrackerIds
import animato.anime.track.simkl.dto.SimklOAuth
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Simkl, which tracks anime, television and film.
 *
 * The first tracker here with no Mihon counterpart, so it is a tracker in its own right rather than
 * a wrapper — see [AnimeOnlyTracker] for what that costs and why nothing manga-side can reach it.
 *
 * Simkl has no rewatching status. [getRewatchingStatus] answers with a number that is not a status,
 * so "started watching again" leaves the entry alone rather than writing something Simkl would
 * reject — the same arrangement Bangumi needs.
 */
class AnimeSimkl : AnimeOnlyTracker(AnimeTrackerIds.SIMKL, "Simkl") {

    companion object {
        const val WATCHING = 1L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val NOT_INTERESTING = 4L
        const val PLAN_TO_WATCH = 5L

        private const val NO_REWATCHING_STATUS = 0L

        private val SCORE_LIST = IntRange(0, 10).map(Int::toString).toImmutableList()
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { SimklInterceptor(this) }

    private val api by lazy { SimklApi(client, interceptor) }

    override fun getLogo() = R.drawable.ic_tracker_simkl

    override fun getScoreList(): ImmutableList<String> = SCORE_LIST

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toInt().toString()

    override fun getStatusListAnime(): List<Long> =
        listOf(WATCHING, COMPLETED, ON_HOLD, NOT_INTERESTING, PLAN_TO_WATCH)

    override fun getWatchingStatus(): Long = WATCHING

    override fun getRewatchingStatus(): Long = NO_REWATCHING_STATUS

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        WATCHING -> AYMR.strings.watching
        COMPLETED -> MR.strings.completed
        ON_HOLD -> MR.strings.on_hold
        NOT_INTERESTING -> AYMR.strings.not_interesting
        PLAN_TO_WATCH -> AYMR.strings.plan_to_watch
        else -> null
    }

    override suspend fun update(track: AnimeTrack, didWatchEpisode: Boolean): AnimeTrack {
        if (track.status != COMPLETED && didWatchEpisode) {
            val finished = track.total_episodes > 0 &&
                track.last_episode_seen.toLong() == track.total_episodes
            track.status = if (finished) COMPLETED else WATCHING
        }
        return api.updateLibAnime(track)
    }

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.findLibAnime(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            api.addLibAnime(track)
        }
    }

    /**
     * All three of Simkl's catalogues, because an anime is filed under whichever its entry says —
     * a film adaptation is under `movie` and a live-action series under `tv`, and searching only
     * `anime` would find neither.
     */
    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> =
        api.searchAnime(query, "anime") +
            api.searchAnime(query, "tv") +
            api.searchAnime(query, "movie")

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        api.findLibAnime(track)?.let { remoteTrack ->
            track.copyPersonalFrom(remoteTrack)
            track.total_episodes = remoteTrack.total_episodes
        }
        return track
    }

    override suspend fun login(username: String, password: String) = login(password)

    /**
     * Finishes the OAuth exchange the browser started. [AnimeTrackLoginActivity] hands over the
     * code; the account id is stored where a username would go, since Simkl has no username.
     */
    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            logout()
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.trackToken(this).delete()
        interceptor.newAuth(null)
    }

    fun saveToken(oauth: SimklOAuth?) {
        if (oauth == null) {
            trackPreferences.trackToken(this).delete()
        } else {
            trackPreferences.trackToken(this).set(json.encodeToString(oauth))
        }
    }

    fun restoreToken(): SimklOAuth? = try {
        json.decodeFromString<SimklOAuth>(trackPreferences.trackToken(this).get())
    } catch (e: Exception) {
        null
    }
}
