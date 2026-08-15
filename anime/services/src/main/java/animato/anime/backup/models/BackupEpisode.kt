package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.items.episode.model.Episode

/**
 * One episode as an Aniyomi backup stores it.
 *
 * [totalSeconds] sits at 16 rather than next to [lastSecondSeen] because it was added long after
 * the numbers around it were taken. That is the whole reason for the odd ordering.
 */
@Serializable
data class BackupEpisode(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val scanlator: String? = null,
    @ProtoNumber(4) val seen: Boolean = false,
    @ProtoNumber(5) val bookmark: Boolean = false,
    @ProtoNumber(6) val lastSecondSeen: Long = 0,
    @ProtoNumber(16) val totalSeconds: Long = 0,
    @ProtoNumber(7) val dateFetch: Long = 0,
    @ProtoNumber(8) val dateUpload: Long = 0,
    @ProtoNumber(9) val episodeNumber: Float = 0F,
    @ProtoNumber(10) val sourceOrder: Long = 0,
    @ProtoNumber(11) val lastModifiedAt: Long = 0,
    @ProtoNumber(12) val version: Long = 0,
    @ProtoNumber(13) val memo: ByteArray = JsonObjectEmptyBytes,

    // Aniyomi's own.
    @ProtoNumber(501) val fillermark: Boolean = false,
    @ProtoNumber(502) val summary: String? = null,
    @ProtoNumber(503) val previewUrl: String? = null,
) {

    fun toEpisode(): Episode {
        return Episode.create().copy(
            url = url,
            name = name,
            episodeNumber = episodeNumber.toDouble(),
            scanlator = scanlator,
            summary = summary,
            previewUrl = previewUrl,
            seen = seen,
            bookmark = bookmark,
            fillermark = fillermark,
            lastSecondSeen = lastSecondSeen,
            totalSeconds = totalSeconds,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            sourceOrder = sourceOrder,
            lastModifiedAt = lastModifiedAt,
            version = version,
            memo = MemoColumnAdapter.decode(memo),
        )
    }

    // Same reason as BackupAnime: the memo blob costs the generated pair.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupEpisode) return false
        return url == other.url &&
            name == other.name &&
            scanlator == other.scanlator &&
            seen == other.seen &&
            bookmark == other.bookmark &&
            lastSecondSeen == other.lastSecondSeen &&
            totalSeconds == other.totalSeconds &&
            dateFetch == other.dateFetch &&
            dateUpload == other.dateUpload &&
            episodeNumber == other.episodeNumber &&
            sourceOrder == other.sourceOrder &&
            lastModifiedAt == other.lastModifiedAt &&
            version == other.version &&
            memo.contentEquals(other.memo) &&
            fillermark == other.fillermark &&
            summary == other.summary &&
            previewUrl == other.previewUrl
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + episodeNumber.hashCode()
        result = 31 * result + memo.contentHashCode()
        return result
    }
}

/**
 * Reads an episode row straight into its backup form.
 *
 * The parameter order is the column order of `SELECT * FROM episodes` and nothing else. Adding a
 * column to that table means adding a parameter here, in the same position.
 */
val backupEpisodeMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
        summary: String?,
        previewUrl: String?,
        fillermark: Boolean,
        memo: JsonObject,
    ->
    BackupEpisode(
        url = url,
        name = name,
        scanlator = scanlator,
        seen = seen,
        bookmark = bookmark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber.toFloat(),
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = MemoColumnAdapter.encode(memo),
        fillermark = fillermark,
        summary = summary,
        previewUrl = previewUrl,
    )
}
