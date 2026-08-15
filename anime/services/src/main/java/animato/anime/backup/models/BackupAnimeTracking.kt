package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * One tracker entry as an Aniyomi backup stores it.
 *
 * [mediaIdInt] is the reason 100 exists. Tachiyomi 1.x wrote the remote id as an Int at 3, which
 * overflowed for AniList, so 0.x added a Long at 100 and left 3 in place for backups already
 * written. A backup can carry either, never both, so [toTrack] prefers whichever is set.
 */
@Serializable
data class BackupAnimeTracking(
    @ProtoNumber(1) val syncId: Int,
    @ProtoNumber(2) val libraryId: Long,
    @ProtoNumber(3) val mediaIdInt: Int = 0,
    @ProtoNumber(4) val trackingUrl: String = "",
    @ProtoNumber(5) val title: String = "",
    @ProtoNumber(6) val lastEpisodeSeen: Float = 0F,
    @ProtoNumber(7) val totalEpisodes: Int = 0,
    @ProtoNumber(8) val score: Float = 0F,
    @ProtoNumber(9) val status: Int = 0,
    @ProtoNumber(10) val startedWatchingDate: Long = 0,
    @ProtoNumber(11) val finishedWatchingDate: Long = 0,
    @ProtoNumber(12) val private: Boolean = false,
    @ProtoNumber(100) val mediaId: Long = 0,
) {

    fun toTrack(): AnimeTrack {
        return AnimeTrack(
            id = -1,
            animeId = -1,
            trackerId = syncId.toLong(),
            remoteId = if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId,
            libraryId = libraryId,
            title = title,
            lastEpisodeSeen = lastEpisodeSeen.toDouble(),
            totalEpisodes = totalEpisodes.toLong(),
            score = score.toDouble(),
            status = status.toLong(),
            startDate = startedWatchingDate,
            finishDate = finishedWatchingDate,
            remoteUrl = trackingUrl,
            private = private,
        )
    }
}
