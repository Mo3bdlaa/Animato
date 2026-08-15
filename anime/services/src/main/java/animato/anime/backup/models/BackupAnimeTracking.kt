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

/**
 * Reads a tracker row straight into its backup form.
 *
 * The parameter order is the column order of `SELECT * FROM anime_sync`. The remote id goes to 100
 * and never to 3: 3 is the Int field that overflowed, and writing it again would recreate the bug
 * it was added to work around.
 */
val backupAnimeTrackMapper = {
        _: Long,
        _: Long,
        syncId: Long,
        remoteId: Long,
        libraryId: Long?,
        title: String,
        lastEpisodeSeen: Double,
        totalEpisodes: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
        private: Boolean,
    ->
    BackupAnimeTracking(
        syncId = syncId.toInt(),
        // Not null in the 1.x format, so a missing one is written as zero rather than left out.
        libraryId = libraryId ?: 0,
        trackingUrl = remoteUrl,
        title = title,
        lastEpisodeSeen = lastEpisodeSeen.toFloat(),
        totalEpisodes = totalEpisodes.toInt(),
        score = score.toFloat(),
        status = status.toInt(),
        startedWatchingDate = startDate,
        finishedWatchingDate = finishDate,
        private = private,
        mediaId = remoteId,
    )
}
