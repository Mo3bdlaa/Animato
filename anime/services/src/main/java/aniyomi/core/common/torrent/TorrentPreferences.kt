package aniyomi.core.common.torrent

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class TorrentPreferences(
    private val preferenceStore: PreferenceStore,
) {
    /**
     * On by default, because off by default was indistinguishable from broken.
     *
     * With it off the player hands a magnet straight to mpv, which cannot open one and does not
     * say so — it shows a spinner and never stops. Most Stremio stream addons are torrent addons,
     * so the app's most obvious new capability led there on the first try.
     *
     * The legal notice this switch used to carry has not been dropped, only moved: it is shown
     * once before the first torrent actually plays, guarded by [torrServerShownNotice]. A default
     * that is on must still not upload anything on somebody's behalf before they have been told
     * that it does — which is the part of BitTorrent people do not expect, and the part that
     * carries the consequences.
     */
    fun torrServerEnable() = preferenceStore.getBoolean("pref_torrserver_enable", true)
    fun torrServerShownNotice() = preferenceStore.getBoolean("pref_torrserver_shownotice", false)

    fun torrServerPort() = preferenceStore.getString("pref_torrserver_port", "8090")

    /**
     * The trackers announced to on top of whatever a magnet names.
     *
     * ## Why the list was rewritten
     *
     * It was inherited from an anime app and had gone stale: RARBG's two trackers, which closed in
     * 2023, and eight more that no longer resolve — a dead tracker is not free, it is a connect
     * that has to time out before the swarm is asked anything. And it was *only* anime trackers,
     * which was right when every torrent came from a fansub site and is wrong now that most of them
     * arrive from a Stremio addon pointing at general film and television releases.
     *
     * So: the public trackers that are actually up, with the anime ones that are still alive kept
     * alongside them rather than replaced.
     *
     * This is only the default. Anyone who has edited the list keeps their own — a preference is
     * not overwritten by a new default — which is also why the dead entries are not worth a
     * migration.
     */
    fun torrServerTrackers() = preferenceStore.getString(
        "pref_torrserver_tackers",
        """udp://tracker.opentrackr.org:1337/announce
           udp://open.demonii.com:1337/announce
           udp://open.stealth.si:80/announce
           udp://tracker.torrent.eu.org:451/announce
           udp://exodus.desync.com:6969/announce
           udp://explodie.org:6969/announce
           udp://tracker.dler.org:6969/announce
           udp://opentracker.i2p.rocks:6969/announce
           udp://open.tracker.cl:1337/announce
           udp://tracker.tiny-vps.com:6969/announce
           udp://tracker.cyberia.is:6969/announce
           udp://tracker.bittor.pw:1337/announce
           udp://tracker.filemail.com:6969/announce
           udp://p4p.arenabg.com:1337/announce
           http://nyaa.tracker.wf:7777/announce
           http://anidex.moe:6969/announce
           udp://tracker.anirena.com:80/announce
           http://t.nyaatracker.com:80/announce""".replace(" ", ""),
    )

    fun torrServerProxyMode() = preferenceStore.getEnum("pref_torrserver_proxymode", ProxyMode.None)
    fun torrServerProxyUrl() = preferenceStore.getString("pref_torrserver_proxyurl", "")
}

enum class ProxyMode(val value: String) {
    None("tracker"),
    Tracker("tracker"),
    Peers("peers"),
    Full("full"),
}
