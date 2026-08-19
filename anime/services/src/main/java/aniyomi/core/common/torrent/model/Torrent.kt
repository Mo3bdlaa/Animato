package aniyomi.core.common.torrent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Torrent(
    val title: String,
    val hash: String? = null,
    @SerialName("torrent_size")
    val torrentSize: Long? = null,
    val trackers: List<String>? = null,
    @SerialName("file_stats")
    val fileStats: List<FileStats>? = null,

    /*
     * Everything below is only filled in on a status read, and the server omits any of it that is
     * zero — so every field is nullable and none of it can be relied on to arrive. It is the same
     * record either way, which is why it is one class: `add` answers with the parts it knows and
     * `get` answers with the rest once there is something to say.
     */

    /** 0 added, 1 getting info, 2 preloading, 3 working, 4 closed, 5 in db. */
    val stat: Int? = null,
    @SerialName("preloaded_bytes")
    val preloadedBytes: Long? = null,
    @SerialName("preload_size")
    val preloadSize: Long? = null,
    @SerialName("loaded_size")
    val loadedSize: Long? = null,
    @SerialName("download_speed")
    val downloadSpeed: Double? = null,
    @SerialName("active_peers")
    val activePeers: Int? = null,
    @SerialName("total_peers")
    val totalPeers: Int? = null,
    @SerialName("connected_seeders")
    val connectedSeeders: Int? = null,
)

@Serializable
data class FileStats(
    val id: Int? = null,
    val path: String,
    val length: Long,
)
