package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent

/**
 * What a torrent is doing while nothing is on screen yet.
 *
 * ## Why this exists
 *
 * Pressing play on a torrent shows a spinner and then, eventually, a film. In between there is a
 * minute of nothing, and every complaint about torrent playback in this app has been some version
 * of *it loads forever* — including two that turned out to be real bugs and one that turned out to
 * be a torrent working exactly as intended. There was no way to tell those apart from the outside,
 * and there still would not be with a faster server: filling a buffer from a public swarm takes
 * however long it takes, and a spinner that means both "working" and "broken" is worth less than
 * either.
 *
 * So the numbers the server already keeps are put on the screen. The point is not decoration. A
 * preload sitting at zero of thirty megabytes with **no peers** is a dead torrent and the answer is
 * to back out and pick another stream; the same zero with **forty peers** is a slow start and the
 * answer is to wait. Those are opposite actions and the spinner gave the same picture for both.
 */
data class TorrentProgress(
    val stage: Stage,
    val loadedBytes: Long,
    val targetBytes: Long,
    val peers: Int,
    val seeders: Int,
    val bytesPerSecond: Long,
) {
    enum class Stage {
        /** Asking the swarm what is even in this torrent. No bytes of video yet, by definition. */
        FindingPeers,

        /** Filling the buffer that has to exist before the first frame. */
        Buffering,

        /** Enough is in hand; the wait now is the player opening the stream. */
        Ready,
    }

    /**
     * How full the buffer is, or null when the target is not known yet.
     *
     * Null rather than zero, because a bar pinned at the left for twenty seconds says "stuck" and
     * an absent bar says "no answer yet", and the second one is the truth during [Stage.FindingPeers].
     */
    val fraction: Float?
        get() = if (targetBytes > 0) (loadedBytes.toFloat() / targetBytes).coerceIn(0f, 1f) else null

    companion object {
        /**
         * Read a status response, or nothing if there is nothing worth saying yet.
         *
         * Returns null once the torrent is past preloading: at that point the wait belongs to the
         * player rather than to the swarm, and leaving a stale buffer figure on screen would be
         * describing something that already finished.
         */
        fun from(torrent: Torrent): TorrentProgress {
            val preloaded = torrent.preloadedBytes ?: 0L
            val target = torrent.preloadSize ?: 0L
            val peers = torrent.activePeers ?: 0
            val stage = when {
                // The server reports 1 while it is still fetching the metadata — for a magnet that
                // is a swarm lookup, which is the slowest part of a cold start and the part that
                // looks most like a hang.
                torrent.stat == STAT_GETTING_INFO || target <= 0L -> Stage.FindingPeers
                preloaded < target -> Stage.Buffering
                else -> Stage.Ready
            }
            return TorrentProgress(
                stage = stage,
                loadedBytes = if (target > 0) preloaded else torrent.loadedSize ?: 0L,
                targetBytes = target,
                peers = peers,
                seeders = torrent.connectedSeeders ?: 0,
                bytesPerSecond = (torrent.downloadSpeed ?: 0.0).toLong(),
            )
        }

        private const val STAT_GETTING_INFO = 1
    }
}
