package animato.anime.track.myanimelist

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.DeletableAnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListInterceptor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * MyAnimeList, for anime.
 *
 * Delegates to Mihon's tracker for everything about being one — see [AnimeAnilist][
 * animato.anime.track.anilist.AnimeAnilist] for why that is the shape all of these take.
 *
 * The status numbers are Aniyomi's, because they go into the database and into every backup.
 */
class AnimeMyAnimeList(private val delegate: MyAnimeList) :
    Tracker by delegate,
    AnimeTracker,
    DeletableAnimeTracker {

    companion object {
        const val WATCHING = 11L
        const val COMPLETED = 2L
        const val ON_HOLD = 3L
        const val DROPPED = 4L
        const val PLAN_TO_WATCH = 16L
        const val REWATCHING = 17L

        private const val SEARCH_ID_PREFIX = "id:"
        private const val SEARCH_LIST_PREFIX = "my:"

        private val SCORE_LIST = IntRange(0, 10).map(Int::toString).toImmutableList()

        /**
         * The number for a status MAL named.
         *
         * Rewatching is absent on purpose: MAL has no such status, only a flag on top of watching,
         * and the caller checks the flag first.
         */
        fun statusFrom(apiStatus: String): Long = when (apiStatus) {
            "watching" -> WATCHING
            "completed" -> COMPLETED
            "on_hold" -> ON_HOLD
            "dropped" -> DROPPED
            "plan_to_watch" -> PLAN_TO_WATCH
            else -> WATCHING
        }
    }

    private val interceptor by lazy { MyAnimeListInterceptor(delegate) }

    private val api by lazy { AnimeMyAnimeListApi(delegate.id, delegate.client, interceptor) }

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
        if (track.status != COMPLETED) {
            if (didWatchEpisode) {
                track.status = if (
                    track.total_episodes > 0 &&
                    track.last_episode_seen.toLong() == track.total_episodes
                ) {
                    COMPLETED
                } else if (track.status != REWATCHING) {
                    WATCHING
                } else {
                    track.status
                }
            }
        }
        return api.updateItem(track)
    }

    override suspend fun delete(track: DomainAnimeTrack) = api.deleteItem(track)

    override suspend fun bind(track: AnimeTrack, hasSeenEpisodes: Boolean): AnimeTrack {
        val remoteTrack = api.findListItem(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                val isRewatching = track.status == REWATCHING
                track.status = if (!isRewatching && hasSeenEpisodes) WATCHING else track.status
            }

            update(track)
        } else {
            track.status = if (hasSeenEpisodes) WATCHING else PLAN_TO_WATCH
            track.score = 0.0
            api.updateItem(track)
        }
    }

    /**
     * MyAnimeList's search box takes two prefixes Mihon also honours: `id:` to go straight to one
     * entry, and `my:` to search the user's own list rather than the whole site.
     */
    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toLongOrNull()?.let { id ->
                return listOf(api.getAnimeDetails(id))
            }
        }
        if (query.startsWith(SEARCH_LIST_PREFIX)) {
            return api.findListItems(query.substringAfter(SEARCH_LIST_PREFIX))
        }
        return api.searchAnime(query)
    }

    override suspend fun refresh(track: AnimeTrack): AnimeTrack {
        return api.findListItem(track) ?: track
    }
}

/**
 * The name MyAnimeList knows this status by.
 *
 * Rewatching and watching are both "watching": MAL keeps rewatching as a separate flag, which
 * [AnimeMyAnimeListApi] sets alongside this.
 */
fun AnimeTrack.toMyAnimeListStatus(): String? = when (status) {
    AnimeMyAnimeList.WATCHING, AnimeMyAnimeList.REWATCHING -> "watching"
    AnimeMyAnimeList.COMPLETED -> "completed"
    AnimeMyAnimeList.ON_HOLD -> "on_hold"
    AnimeMyAnimeList.DROPPED -> "dropped"
    AnimeMyAnimeList.PLAN_TO_WATCH -> "plan_to_watch"
    else -> null
}
