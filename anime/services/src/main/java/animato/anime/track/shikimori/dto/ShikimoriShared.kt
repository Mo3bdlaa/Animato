package animato.anime.track.shikimori.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Carried from Mihon's Shikimori tracker, which replaced these with generated GraphQL types.

@Serializable
data class SMAddMangaResponse(
    val id: Long,
)

@Serializable
data class SMPoster(
    val mainUrl: String,
)

@Serializable
data class SMAiredDate(
    val date: String?,
)

@Serializable
data class SMPersonRole(
    val person: SMPerson,
    @SerialName("rolesEn")
    val roles: List<String>,
)

@Serializable
data class SMPerson(
    val name: String,
)
