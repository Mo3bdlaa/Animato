package animato.anime.track.simkl.dto

import animato.anime.track.AnimeTrackerIds
import animato.anime.track.simkl.SimklApi.Companion.POSTERS_URL
import animato.anime.track.simkl.toTrackStatus
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What Simkl answers with.
 *
 * Simkl indexes shows, films and anime under one account, and its replies say which of the three a
 * result is. That is why so much here is nullable and so much is chosen by a type string: the same
 * endpoint answers about all of them, and a film has no episode count to report.
 */
@Serializable
data class SimklOAuth(
    @SerialName("access_token")
    val accessToken: String,
)

@Serializable
data class SimklUser(
    val account: SimklUserAccount,
)

@Serializable
data class SimklUserAccount(
    val id: Int,
)

@Serializable
data class SimklSearchResult(
    val ids: SimklSearchResultIds,
    @SerialName("title_romaji")
    val titleRomaji: String? = null,
    val title: String? = null,
    @SerialName("ep_count")
    val epCount: Long? = null,
    val poster: String? = null,
    @SerialName("all_titles")
    val allTitles: List<String>? = null,
    val url: String,
    val status: String? = null,
    val type: String? = null,
    val year: Int? = null,
) {
    fun toTrackSearch(fallbackType: String): AnimeTrackSearch {
        return AnimeTrackSearch.create(AnimeTrackerIds.SIMKL).apply {
            remote_id = ids.simklId
            title = titleRomaji ?: this@SimklSearchResult.title.orEmpty()
            // A film is one "episode", which is what makes progress on it mean watched or not.
            total_episodes = epCount ?: 1
            cover_url = poster?.let { "$POSTERS_URL${it}_m.webp" }.orEmpty()
            summary = allTitles?.joinToString("\n", prefix = "All titles:\n").orEmpty()
            tracking_url = url
            publishing_status = this@SimklSearchResult.status ?: "ended"
            publishing_type = type ?: fallbackType
            start_date = year?.toString().orEmpty()
        }
    }
}

@Serializable
data class SimklSearchResultIds(
    @SerialName("simkl_id")
    val simklId: Long,
)

@Serializable
data class SimklSyncWatched(
    val result: Boolean? = null,
    @SerialName("last_watched")
    val lastWatched: String? = null,
    val list: String? = null,
)

@Serializable
data class SimklSyncResult(
    val anime: List<SimklSyncItem>? = null,
    val tv: List<SimklSyncItem>? = null,
    val movies: List<SimklSyncItem>? = null,
) {
    fun getFromType(type: String): List<SimklSyncItem>? = when (type) {
        "anime" -> anime
        "tv" -> tv
        "movies" -> movies
        else -> throw IllegalArgumentException("Unknown Simkl media type: $type")
    }
}

@Serializable
data class SimklSyncItem(
    val show: SimklSyncResultItem? = null,
    val movie: SimklSyncResultItem? = null,
    @SerialName("total_episodes_count")
    val totalEpisodesCount: Long? = null,
    @SerialName("watched_episodes_count")
    val watchedEpisodesCount: Double? = null,
    @SerialName("user_rating")
    val userRating: Int? = null,
) {
    fun toAnimeTrack(typeName: String, type: String, statusString: String): AnimeTrack {
        val resultData = getFromType(typeName)

        return AnimeTrack.create(AnimeTrackerIds.SIMKL).apply {
            title = resultData.title
            remote_id = resultData.ids.simkl
            if (typeName != "movie") {
                total_episodes = totalEpisodesCount ?: 0L
                last_episode_seen = watchedEpisodesCount ?: 0.0
            } else {
                // A film has no episodes, so its progress is the whole of it or none.
                total_episodes = 1
                last_episode_seen = if (statusString == "completed") 1.0 else 0.0
            }
            score = userRating?.toDouble() ?: 0.0
            status = toTrackStatus(statusString)
            // Relative on purpose: the media type is read back off this to decide which endpoint
            // an update goes to. See SimklApi.
            tracking_url = "/$type/${resultData.ids.simkl}"
        }
    }

    fun getFromType(typeName: String): SimklSyncResultItem = when (typeName) {
        "show" -> requireNotNull(show) { "Simkl answered about a show with no show" }
        "movie" -> requireNotNull(movie) { "Simkl answered about a movie with no movie" }
        else -> throw IllegalArgumentException("Unknown Simkl result type: $typeName")
    }
}

@Serializable
data class SimklSyncResultItem(
    val title: String,
    val ids: SimklSyncResultIds,
)

@Serializable
data class SimklSyncResultIds(
    val simkl: Long,
)
