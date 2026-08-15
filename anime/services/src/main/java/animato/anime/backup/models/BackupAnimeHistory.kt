package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.history.anime.model.AnimeHistory
import java.util.Date

/**
 * One history entry as an Aniyomi backup stores it.
 *
 * The field names are the manga ones because the format is: an anime backup writes the time an
 * episode was last watched into a field called `lastRead`. [readDuration] is written but never
 * read — the anime history table has no column for it — and is kept only so the field number
 * stays taken.
 */
@Serializable
data class BackupAnimeHistory(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val lastRead: Long,
    @ProtoNumber(3) val readDuration: Long = 0,
) {

    fun toHistory(): AnimeHistory {
        return AnimeHistory.create().copy(seenAt = Date(lastRead))
    }
}
