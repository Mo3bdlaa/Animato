package animato.anime.track.jellyfin

import animato.anime.track.jellyfin.dto.JFItem
import animato.anime.track.jellyfin.dto.JFItemList
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * A Jellyfin server, asked about what the user has watched.
 *
 * Unlike every other tracker here there is no catalogue to search and no id to look up: the anime's
 * own url *is* the server address of the item, because the Jellyfin extension put it there. So the
 * work is entirely in reading that url — its fragment says whether the item is a film or an episode
 * of a series, and everything else follows from that.
 */
class JellyfinApi(
    private val trackId: Long,
    private val client: OkHttpClient,
) {

    private val json: Json by injectLazy()

    suspend fun getTrackSearch(url: String): AnimeTrackSearch = withIOContext {
        try {
            val httpUrl = url.toHttpUrl()
            val fragment = requireNotNull(httpUrl.fragment) { "A Jellyfin url must say what it points at" }

            val track = with(json) {
                client.newCall(GET(url)).awaitSuccess().parseAs<JFItem>().toTrackSearch()
            }.apply { tracking_url = url }

            if (fragment.startsWith("seriesId")) trackFromSeries(track, httpUrl) else track
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not read the Jellyfin item at $url" }
            throw e
        }
    }

    suspend fun updateProgress(track: AnimeTrack): AnimeTrack {
        val httpUrl = track.tracking_url.toHttpUrl()
        val fragment = requireNotNull(httpUrl.fragment) { "A Jellyfin url must say what it points at" }

        val itemId = if (fragment.startsWith("movie")) {
            httpUrl.pathSegments.last()
        } else {
            episodesOf(httpUrl)
                .firstOrNull { it.indexNumber?.isAt(track.last_episode_seen) == true }
                ?.id
        }

        if (itemId != null) {
            // Jellyfin marks whole items played; there is no partial position to send.
            val playedUrl = httpUrl.newBuilder().apply {
                fragment(null)
                removePathSegment(3)
                removePathSegment(2)
                addPathSegment("PlayedItems")
                addPathSegment(itemId)
                addQueryParameter("DatePlayed", DATE_FORMATTER.format(Date()))
            }.build().toString()

            client.newCall(POST(playedUrl)).awaitSuccess()
        }

        // Read it back rather than assume: the server decides what "watched" now means.
        return getTrackSearch(track.tracking_url)
    }

    private fun JFItem.toTrackSearch(): AnimeTrackSearch = AnimeTrackSearch.create(trackId).also {
        it.title = name
        // Right for a film, and replaced by the real count if this turns out to be a series.
        it.total_episodes = 1
        if (userData.played) {
            it.last_episode_seen = 1.0
            it.status = AnimeJellyfin.COMPLETED
        } else {
            it.last_episode_seen = 0.0
            it.status = AnimeJellyfin.UNSEEN
        }
    }

    /**
     * Progress across a season, which Jellyfin reports per episode rather than as a position.
     *
     * The position taken is the last *continuously* watched episode, not the highest one: someone
     * who skipped ahead has not watched what they skipped, and calling it progress would mark it
     * watched everywhere else too.
     */
    private suspend fun trackFromSeries(track: AnimeTrackSearch, url: HttpUrl): AnimeTrackSearch {
        val episodes = episodesOf(url)
        if (episodes.isEmpty()) return track

        val totalEpisodes = episodes.last().indexNumber ?: return track
        val firstUnwatched = episodes.indexOfFirst { !it.userData.played }

        return track.apply {
            total_episodes = totalEpisodes
            when (firstUnwatched) {
                0 -> {
                    last_episode_seen = 0.0
                    status = AnimeJellyfin.UNSEEN
                }
                -1 -> {
                    last_episode_seen = totalEpisodes.toDouble()
                    status = AnimeJellyfin.COMPLETED
                }
                else -> {
                    last_episode_seen = (episodes[firstUnwatched - 1].indexNumber ?: 0L).toDouble()
                    status = AnimeJellyfin.WATCHING
                }
            }
        }
    }

    private suspend fun episodesOf(url: HttpUrl): List<JFItem> {
        val episodesUrl = url.newBuilder().apply {
            encodedPath("/")
            fragment(null)
            encodedQuery(null)

            addPathSegment("Shows")
            // The fragment carries the series id after the season it was reached through.
            addPathSegment(requireNotNull(url.fragment).split(",").last())
            addPathSegment("Episodes")
            addQueryParameter("seasonId", url.pathSegments.last())
            addQueryParameter("userId", url.pathSegments[1])
            addQueryParameter("Fields", "Overview,MediaSources")
        }.build()

        return with(json) {
            client.newCall(GET(episodesUrl)).awaitSuccess().parseAs<JFItemList>()
        }.items
    }

    /**
     * Episode numbers arrive as whole numbers and progress is stored as a double, so they are
     * compared with a tolerance rather than converted.
     */
    private fun Long.isAt(position: Double): Boolean = abs(this - position) < 0.001

    private companion object {
        val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }
}
