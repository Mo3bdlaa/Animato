package eu.kanade.tachiyomi.torrentutils

import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy

/**
 * How [TorrentUtils] reaches the torrent client without this module depending on it.
 *
 * In Aniyomi this object talks to `TorrentServerApi` directly, because there the torrent client
 * lives below the source API. Here it lives above it — in `:anime:services`, alongside the
 * downloader — and the dependency points the other way. The app registers an implementation of
 * this in Injekt at startup; extensions never see the seam, because [TorrentUtils]'s own
 * signatures are unchanged.
 */
interface TorrentInfoProvider {
    suspend fun getTorrentInfo(url: String, title: String): TorrentInfo
}

/**
 * The torrent half of the extension API, absent until a device found it.
 *
 * Torrent-backed sources call this at video time to turn a magnet link or a `.torrent` file into
 * something playable. It was never ported from the donor, so every one of those sources compiled
 * against a class that did not exist in the app: episodes listed fine, and the moment a video was
 * asked for, the source died on `NoClassDefFoundError` — which the player swallowed into
 * "no available videos".
 *
 * The public signatures — including the hidden blocking overload — are the donor's exactly,
 * because extensions link against them as compiled bytecode.
 */
object TorrentUtils {

    private val provider: TorrentInfoProvider by injectLazy()

    suspend fun getTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        return provider.getTorrentInfo(url, title)
    }

    // A suspend function has a different signature in the JVM than a regular function (an additional Continuation
    // parameter is added by the Kotlin compiler). We add another overload of getTorrentInfo that is not a suspend
    // function so that extensions targetting other forks where getTorrentInfo was not a suspend function can still
    // work.
    @Deprecated(
        message = "This overload of getTorrentInfo exists only for binary compatibility with extensions targeting" +
            " other forks where getTorrentInfo was not a suspend function",
        level = DeprecationLevel.HIDDEN,
    )
    @JvmName("getTorrentInfo")
    fun blockingShimForGetTorrentInfo(
        url: String,
        title: String,
    ): TorrentInfo {
        return runBlocking {
            getTorrentInfo(url, title)
        }
    }
}
