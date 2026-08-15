package animato.anime.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What MyAnimeList answers with about anime.
 *
 * Mihon's models cannot be reused: they ask for `num_chapters`, and the anime endpoint answers with
 * `num_episodes`. The names on the wire are what differ, so the types have to.
 */
@Serializable
data class MALAnime(
    val id: Long,
    val title: String,
    val synopsis: String = "",
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALAnimeCovers? = null,
    val status: String = "",
    @SerialName("media_type")
    val mediaType: String = "",
    @SerialName("start_date")
    val startDate: String? = null,
)

@Serializable
data class MALAnimeCovers(
    val large: String = "",
)

/**
 * An anime as it appears on the user's own list, with their progress attached.
 */
@Serializable
data class MALListAnimeItem(
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    @SerialName("my_list_status")
    val myListStatus: MALListAnimeItemStatus? = null,
)

@Serializable
data class MALListAnimeItemStatus(
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    val status: String = "",
    @SerialName("num_episodes_watched")
    val numEpisodesWatched: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
)

/**
 * A page of search results, and the user's own list, which MyAnimeList answers with in one shape.
 *
 * Not Mihon's `MALSearchResult`, even though the envelope is identical. Its node is a `MALManga`,
 * which requires `num_chapters`, `status`, `media_type` and a cover — none of which a search node
 * is guaranteed to carry, and none of which an anime node would carry under those names anyway. A
 * search answers with an id and a title; that is what is declared here, and everything past it is
 * fetched per entry.
 */
@Serializable
data class MALAnimeSearchResult(
    val data: List<MALAnimeSearchNode> = emptyList(),
    val paging: MALAnimeSearchPaging = MALAnimeSearchPaging(),
)

@Serializable
data class MALAnimeSearchNode(
    val node: MALAnimeSearchNodeItem,
)

@Serializable
data class MALAnimeSearchNodeItem(
    val id: Long,
    val title: String = "",
)

@Serializable
data class MALAnimeSearchPaging(
    val next: String? = null,
)
