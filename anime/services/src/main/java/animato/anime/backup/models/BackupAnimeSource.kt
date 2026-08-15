package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * The name a backup remembers for a source id.
 *
 * Nothing is restored from this. It exists so that a missing extension can be named in the report
 * before the restore starts, rather than leaving the user with a library of entries from "1234567".
 */
@Serializable
data class BackupAnimeSource(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val sourceId: Long,
)
