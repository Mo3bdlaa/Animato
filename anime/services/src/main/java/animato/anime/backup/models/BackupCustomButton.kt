package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * One of the player's custom buttons as an Aniyomi backup stores it.
 *
 * These are Lua snippets the user wrote themselves and cannot get back from anywhere else, which
 * is why they are worth carrying across even though nothing else in the app depends on them.
 */
@Serializable
data class BackupCustomButton(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val isFavorite: Boolean = false,
    @ProtoNumber(3) val sortIndex: Long = 0,
    @ProtoNumber(4) val content: String = "",
    @ProtoNumber(5) val longPressContent: String = "",
    @ProtoNumber(6) val onStartup: String = "",
)
