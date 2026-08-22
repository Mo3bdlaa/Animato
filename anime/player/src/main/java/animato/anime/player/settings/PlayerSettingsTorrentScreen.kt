package animato.anime.player.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import animato.ui.settings.editTextInfoPreference
import animato.ui.settings.multiLineEditTextPreference
import aniyomi.core.common.torrent.ProxyMode
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object PlayerSettingsTorrentScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_torrents

    @Composable
    override fun getPreferences(): List<Preference> {
        var isDialogShown by rememberSaveable { mutableStateOf(false) }

        val torrentPreferences = remember { Injekt.get<TorrentPreferences>() }
        val torrentApi = remember { Injekt.get<TorrentServerApi>() }

        val torrentEnablePref = torrentPreferences.torrServerEnable()
        val torrentEnable by torrentEnablePref.collectAsState()
        val shownNoticePref = torrentPreferences.torrServerShownNotice()
        val shownNotice by shownNoticePref.collectAsState()

        val portPref = torrentPreferences.torrServerPort()
        val trackersPref = torrentPreferences.torrServerTrackers()
        val trackers by trackersPref.collectAsState()
        val proxyModePref = torrentPreferences.torrServerProxyMode()
        val proxyMode by proxyModePref.collectAsState()
        val proxyUrlPref = torrentPreferences.torrServerProxyUrl()

        if (isDialogShown) {
            AlertDialog(
                onDismissRequest = { isDialogShown = false },
                title = { Text(stringResource(AYMR.strings.pref_player_torrents_notice)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)) {
                        Text(stringResource(AYMR.strings.pref_player_torrents_notice_text))
                        Text(stringResource(AYMR.strings.pref_player_torrents_notice_footer))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isDialogShown = false }) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = torrentEnablePref,
                title = stringResource(AYMR.strings.pref_player_torrents_enable),
                onValueChanged = {
                    if (it && !shownNotice) {
                        isDialogShown = true
                        shownNoticePref.set(true)
                    }
                    if (!it) {
                        TorrentServerService.stop()
                    }
                    true
                },
            ),
            /*
             * Uploading, as a choice rather than as a default.
             *
             * BitTorrent works because peers give pieces back, and an app that only takes is a
             * worse citizen of every swarm it joins. It is still off by default: a phone on a
             * mobile plan pays for every byte it sends and pays again in battery, and uploading is
             * the half of the protocol that makes you a *source* rather than one more downloader.
             * Neither is something to spend on somebody's behalf before they have chosen it.
             *
             * The subtitle says what turning it on buys, because the trade genuinely runs both
             * ways — clients favour peers that share, so a torrent that never uploads can be
             * served more slowly than one that does.
             */
            Preference.PreferenceItem.SwitchPreference(
                preference = torrentPreferences.torrServerUpload(),
                title = stringResource(AYMR.strings.pref_player_torrents_upload),
                subtitle = stringResource(AYMR.strings.pref_player_torrents_upload_summary),
                enabled = torrentEnable,
                // Pushed to a running server rather than left for the next start, which is what
                // every other setting on this screen does. Somebody who has just switched
                // uploading off means *now* — a switch that reads off while the thing it names
                // carries on until the service happens to restart is the worst kind of wrong, and
                // on this particular setting it is wrong about somebody's data and their exposure.
                onValueChanged = { wantsUpload ->
                    // Written here rather than left to the caller, because the tune below reads
                    // the preference and the caller writes it only after this returns.
                    torrentPreferences.torrServerUpload().set(wantsUpload)
                    if (torrentApi.getPort() != 0) {
                        withIOContext { torrentApi.tuneForStreaming() }
                    }
                    true
                },
            ),
            editTextInfoPreference(
                preference = portPref,
                dialogSubtitle = stringResource(AYMR.strings.pref_player_torrents_port_summary),
                title = stringResource(AYMR.strings.pref_player_torrents_port),
                validate = { pref ->
                    val port = pref.toIntOrNull()
                        ?: return@editTextInfoPreference false

                    if (port !in 0..65535) {
                        return@editTextInfoPreference false
                    }

                    true
                },
                errorMessage = { _ ->
                    stringResource(AYMR.strings.pref_player_torrents_port_error)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = torrentEnable,
            ),
            multiLineEditTextPreference(
                preference = trackersPref,
                title = stringResource(AYMR.strings.pref_player_torrents_trackers),
                subtitle = remember(trackers) {
                    trackers.lines().take(2)
                        .joinToString(
                            separator = "\n",
                            postfix = if (trackers.lines().size > 2) "\n..." else "",
                        )
                },
                onValueChanged = {
                    TorrentServerService.stop()
                    true
                },
                enabled = torrentEnable,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_player_torrents_trackers_reset),
                enabled = remember(torrentEnable, trackersPref) {
                    torrentEnable && trackersPref.get() != trackersPref.defaultValue()
                },
                onClick = {
                    trackersPref.delete()
                },
            ),
            Preference.PreferenceItem.ListPreference(
                preference = proxyModePref,
                entries = ProxyMode.entries.associateWith {
                    val titleRes = when (it) {
                        ProxyMode.None -> AYMR.strings.pref_player_torrents_proxy_mode_none
                        ProxyMode.Tracker -> AYMR.strings.pref_player_torrents_proxy_mode_tracker
                        ProxyMode.Peers -> AYMR.strings.pref_player_torrents_proxy_mode_peers
                        ProxyMode.Full -> AYMR.strings.pref_player_torrents_proxy_mode_full
                    }
                    stringResource(titleRes)
                }.toPersistentMap(),
                title = stringResource(AYMR.strings.pref_player_torrents_proxy_mode),
                enabled = torrentEnable,
            ),
            editTextInfoPreference(
                preference = proxyUrlPref,
                title = stringResource(AYMR.strings.pref_player_torrents_proxy_url),
                dialogSubtitle = stringResource(AYMR.strings.pref_player_torrents_proxy_url_dialog),
                validate = { pref ->
                    val uri = pref.toUri()

                    if (uri.scheme == null || uri.host == null) {
                        return@editTextInfoPreference false
                    }

                    if (uri.scheme !in setOf("http", "https", "socks4", "socks4a", "socks5", "socks5h")) {
                        return@editTextInfoPreference false
                    }

                    true
                },
                errorMessage = { pref ->
                    val uri = pref.toUri()

                    if (uri.scheme == null || uri.host == null) {
                        return@editTextInfoPreference stringResource(
                            AYMR.strings.pref_player_torrents_proxy_url_invalid_uri,
                        )
                    }

                    if (uri.scheme !in setOf("http", "https", "socks4", "socks4a", "socks5", "socks5h")) {
                        return@editTextInfoPreference stringResource(
                            AYMR.strings.pref_player_torrents_proxy_url_invalid_protocol,
                            uri.scheme!!,
                        )
                    }

                    ""
                },
                enabled = torrentEnable && proxyMode != ProxyMode.None,
            ),
        )
    }
}
