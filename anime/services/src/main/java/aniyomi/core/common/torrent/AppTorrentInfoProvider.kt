package aniyomi.core.common.torrent

import aniyomi.core.common.torrent.model.Torrent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.torrentutils.TorrentInfoProvider
import eu.kanade.tachiyomi.torrentutils.model.DeadTorrentException
import eu.kanade.tachiyomi.torrentutils.model.TorrentFile
import eu.kanade.tachiyomi.torrentutils.model.TorrentInfo
import java.net.SocketTimeoutException

/**
 * The app-side half of `TorrentUtils` — the donor's implementation, behind the seam that
 * `TorrentInfoProvider` documents. Registered in Injekt at startup; called only from extensions.
 */
class AppTorrentInfoProvider(
    private val torrentServerApi: TorrentServerApi,
    private val network: NetworkHelper,
) : TorrentInfoProvider {

    override suspend fun getTorrentInfo(url: String, title: String): TorrentInfo {
        val torrent: Torrent = if (url.startsWith("magnet")) {
            // Magnet links need to be added to the torrent server to retrieve their information
            try {
                torrentServerApi.addTorrent(url, title, "", "", false)
            } catch (_: SocketTimeoutException) {
                throw DeadTorrentException()
            } catch (_: Exception) {
                throw DisabledTorrServerException()
            }
        } else {
            // For torrent files we can parse the information out of the file itself without
            // starting the torrent server
            network.client.newCall(GET(url)).awaitSuccess().use { response ->
                TorrentHelpers.parseTorrentDetailsFromTorrentFileContent(response.body.byteStream())
            }
        }
        return torrentToTorrentInfo(torrent, title)
    }

    private fun torrentToTorrentInfo(torrent: Torrent, overrideTitle: String?): TorrentInfo {
        return TorrentInfo(
            overrideTitle ?: torrent.title,
            torrent.fileStats?.map { file ->
                TorrentFile(file.path, file.id ?: 0, file.length, torrent.hash!!, torrent.trackers ?: emptyList())
            } ?: emptyList(),
            torrent.hash!!,
            torrent.torrentSize!!,
            torrent.trackers ?: emptyList(),
        )
    }
}
