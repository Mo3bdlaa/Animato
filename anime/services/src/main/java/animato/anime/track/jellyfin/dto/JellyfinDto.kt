package animato.anime.track.jellyfin.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a Jellyfin server answers with. Its JSON is PascalCase, hence every name being spelled out.
 */
@Serializable
data class JFItem(
    @SerialName("Name") val name: String,
    @SerialName("Id") val id: String,
    @SerialName("UserData") val userData: JFUserData,
    @SerialName("IndexNumber") val indexNumber: Long? = null,
)

@Serializable
data class JFUserData(
    @SerialName("Played") val played: Boolean,
)

@Serializable
data class JFItemList(
    @SerialName("Items") val items: List<JFItem>,
)
