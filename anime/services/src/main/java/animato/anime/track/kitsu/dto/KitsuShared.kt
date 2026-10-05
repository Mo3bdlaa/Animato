package animato.anime.track.kitsu.dto

import kotlinx.serialization.Serializable

// Carried from Mihon's Kitsu tracker, which replaced these with generated GraphQL types.

@Serializable
data class KitsuSearchResult(
    val media: KitsuSearchResultData,
)

@Serializable
data class KitsuSearchResultData(
    val key: String,
)

@Serializable
data class KitsuSearchItemCover(
    val original: String?,
)
