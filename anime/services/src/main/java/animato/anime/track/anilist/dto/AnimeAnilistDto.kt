package animato.anime.track.anilist.dto

import animato.anime.track.anilist.AnimeAnilist
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.dto.ALFuzzyDate
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.util.lang.htmlDecode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * What AniList answers with about anime.
 *
 * Mihon's own AniList models cannot be reused. Its `ALSearchItem` asks for `chapters` and `staff`,
 * which anime does not have — an anime has `episodes` and studios — and the fields are required, so
 * decoding an anime response into it fails rather than leaving them empty. The two are the same
 * shape only until you look at them.
 *
 * `ALFuzzyDate` is Mihon's, unchanged: a partial date is a partial date.
 */
@Serializable
data class ALAnimeSearchResult(
    val data: ALAnimeSearchPage,
)

@Serializable
data class ALAnimeSearchPage(
    @SerialName("Page")
    val page: ALAnimeSearchMedia,
)

@Serializable
data class ALAnimeSearchMedia(
    val media: List<ALAnimeSearchItem>,
)

@Serializable
data class ALAnimeSearchItem(
    val id: Long,
    val title: ALAnimeTitle,
    val coverImage: ALAnimeCover,
    val description: String? = null,
    val format: String = "",
    val status: String? = null,
    val startDate: ALFuzzyDate = UNKNOWN_DATE,
    val episodes: Long? = null,
    val averageScore: Int? = null,
    val studios: ALStudios = ALStudios(),
) {

    fun toTrackSearch() = AnimeTrackSearch.create(TrackerManager.ANILIST).also {
        it.remote_id = id
        it.title = title.userPreferred
        it.total_episodes = episodes ?: 0
        it.cover_url = coverImage.large
        it.summary = description?.htmlDecode().orEmpty()
        it.score = (averageScore ?: -1).toDouble()
        it.tracking_url = ANIME_URL_PREFIX + id
        it.publishing_status = status.orEmpty()
        it.publishing_type = format.replace("_", "-")
        it.start_date = startDate.toEpochMilli().takeIf { millis -> millis != 0L }?.let(::formatDate).orEmpty()
        // The main studio is the one worth naming; the rest are usually production committees.
        it.authors = studios.edges
            .filter { edge -> edge.isMain }
            .ifEmpty { studios.edges }
            .take(3)
            .map { edge -> edge.node.name }
    }
}

@Serializable
data class ALAnimeTitle(
    val userPreferred: String,
)

@Serializable
data class ALAnimeCover(
    val large: String,
)

@Serializable
data class ALStudios(
    val edges: List<ALStudioEdge> = emptyList(),
)

@Serializable
data class ALStudioEdge(
    val isMain: Boolean = false,
    val node: ALStudioNode,
)

@Serializable
data class ALStudioNode(
    val name: String,
)

@Serializable
data class ALAnimeListQueryResult(
    val data: ALAnimeListPage,
)

@Serializable
data class ALAnimeListPage(
    @SerialName("Page")
    val page: ALAnimeListMedia,
)

@Serializable
data class ALAnimeListMedia(
    val mediaList: List<ALAnimeListItem>,
)

@Serializable
data class ALAnimeListItem(
    val id: Long,
    val status: String,
    @SerialName("scoreRaw")
    val scoreRaw: Int = 0,
    val progress: Int = 0,
    val private: Boolean = false,
    val startedAt: ALFuzzyDate = UNKNOWN_DATE,
    val completedAt: ALFuzzyDate = UNKNOWN_DATE,
    val media: ALAnimeSearchItem,
) {

    fun toTrack() = AnimeTrack.create(TrackerManager.ANILIST).also {
        it.remote_id = media.id
        it.title = media.title.userPreferred
        it.status = toTrackStatus()
        it.score = scoreRaw.toDouble()
        it.started_watching_date = startedAt.toEpochMilli()
        it.finished_watching_date = completedAt.toEpochMilli()
        it.last_episode_seen = progress.toDouble()
        it.library_id = id
        it.total_episodes = media.episodes ?: 0
        it.private = private
    }

    /**
     * AniList has one set of list statuses for both kinds of media; the anime ones are separate
     * numbers here so that a status can be shown with the right word.
     */
    private fun toTrackStatus() = when (status) {
        "CURRENT" -> AnimeAnilist.WATCHING
        "COMPLETED" -> AnimeAnilist.COMPLETED
        "PAUSED" -> AnimeAnilist.ON_HOLD
        "DROPPED" -> AnimeAnilist.DROPPED
        "PLANNING" -> AnimeAnilist.PLAN_TO_WATCH
        "REPEATING" -> AnimeAnilist.REWATCHING
        else -> throw NotImplementedError("Unknown AniList status: $status")
    }
}

private fun formatDate(epochMillis: Long): String = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(epochMillis)
} catch (_: IllegalArgumentException) {
    ""
}

private const val ANIME_URL_PREFIX = "https://anilist.co/anime/"

// AniList omits a date it does not know rather than sending nulls, and Mihon's model has no
// default of its own, so absence has to be spelled out here.
private val UNKNOWN_DATE = ALFuzzyDate(year = null, month = null, day = null)
