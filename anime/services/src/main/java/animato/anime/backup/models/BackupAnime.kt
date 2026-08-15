package animato.anime.backup.models

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.entries.anime.model.Anime

/**
 * One anime as an Aniyomi backup stores it.
 *
 * The field numbers are not ours to choose: they are what Aniyomi wrote, and a backup made years
 * ago has to decode into this class unchanged. Numbers 1-17 are the shape Tachiyomi 1.x defined and
 * every fork still speaks; 100-112 are the 0.x additions; 500 and up are Aniyomi's own. Nothing
 * here may be renumbered, and a new field takes the next free number rather than a tidy one.
 *
 * Gaps are deliberate and documented where Aniyomi left one — 501 in particular is burnt.
 */
@Serializable
data class BackupAnime(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: List<String> = emptyList(),
    @ProtoNumber(8) val status: Int = 0,
    // Called cover in 1.x.
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    // 10-12 are 1.x values that 0.x never wrote.
    @ProtoNumber(13) val dateAdded: Long = 0,
    // 15 is a 1.x value that 0.x never wrote.
    @ProtoNumber(16) val episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(17) val categories: List<Long> = emptyList(),
    @ProtoNumber(18) val tracking: List<BackupAnimeTracking> = emptyList(),
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(101) val episodeFlags: Int = 0,
    // 102 held a history model whose proto numbering was invalid; 104 replaced it.
    @ProtoNumber(103) val viewerFlags: Int = 0,
    @ProtoNumber(104) val history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(105) val updateStrategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(107) val favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) val version: Long = 0,
    @ProtoNumber(112) val memo: ByteArray = JsonObjectEmptyBytes,

    // Aniyomi's own.
    @ProtoNumber(500) val backgroundUrl: String? = null,
    // 501 is burnt: an Aniyomi build wrote a value there that cannot be read back.
    @ProtoNumber(502) val parentId: Long? = null,
    // Only ever used to match a season to its parent within one backup. It is not a database id
    // and must not be restored as one.
    @ProtoNumber(503) val id: Long? = null,
    @ProtoNumber(504) val seasonFlags: Long = 0,
    @ProtoNumber(505) val seasonNumber: Double = -1.0,
    @ProtoNumber(506) val seasonSourceOrder: Long = 0,
    @ProtoNumber(507) val fetchType: FetchType = FetchType.Episodes,
) {

    /**
     * The library anime this entry describes, with [parentId] deliberately left out.
     *
     * The backup's parent id belongs to the backup's own id space. Writing it into the database
     * would point the season at whatever row happens to hold that number here. The restorer sets
     * the parent once it knows the id the parent actually got.
     */
    fun toAnime(): Anime {
        return Anime.create().copy(
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            backgroundUrl = backgroundUrl,
            favorite = favorite,
            source = source,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags.toLong(),
            episodeFlags = episodeFlags.toLong(),
            updateStrategy = updateStrategy,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            version = version,
            fetchType = fetchType,
            seasonFlags = seasonFlags,
            seasonNumber = seasonNumber,
            seasonSourceOrder = seasonSourceOrder,
            memo = MemoColumnAdapter.decode(memo),
        )
    }

    // A ByteArray field costs the data class its generated equals/hashCode, and protobuf gives us
    // no way to avoid one for the memo blob. Comparing by content is what the rest of the class
    // already means.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupAnime) return false
        return source == other.source &&
            url == other.url &&
            title == other.title &&
            artist == other.artist &&
            author == other.author &&
            description == other.description &&
            genre == other.genre &&
            status == other.status &&
            thumbnailUrl == other.thumbnailUrl &&
            dateAdded == other.dateAdded &&
            episodes == other.episodes &&
            categories == other.categories &&
            tracking == other.tracking &&
            favorite == other.favorite &&
            episodeFlags == other.episodeFlags &&
            viewerFlags == other.viewerFlags &&
            history == other.history &&
            updateStrategy == other.updateStrategy &&
            lastModifiedAt == other.lastModifiedAt &&
            favoriteModifiedAt == other.favoriteModifiedAt &&
            version == other.version &&
            memo.contentEquals(other.memo) &&
            backgroundUrl == other.backgroundUrl &&
            parentId == other.parentId &&
            id == other.id &&
            seasonFlags == other.seasonFlags &&
            seasonNumber == other.seasonNumber &&
            seasonSourceOrder == other.seasonSourceOrder &&
            fetchType == other.fetchType
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + episodes.hashCode()
        result = 31 * result + memo.contentHashCode()
        return result
    }
}
