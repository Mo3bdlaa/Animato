package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import animato.anime.net.ProxyKind
import animato.anime.net.ProxyPreferences
import animato.anime.net.xray.XrayLink
import animato.app.xray.XrayController
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One proxy, for everything the app asks for.
 *
 * ## What this is and is not
 *
 * It is the answer to a request for an in-app VPN, and it is deliberately not one. A VPN carries
 * the whole device and needs a service to connect to; this carries what the app asks for and needs
 * only an address the person already has. The screen says so in as many words rather than letting
 * the word *proxy* be read as the other thing — because the difference is exactly the thing
 * somebody would be relying on.
 *
 * ## Why the reach is stated per kind
 *
 * A SOCKS proxy carries connections and covers everything Java opens, which is the whole app —
 * except mpv, which does its networking in ffmpeg and understands only an HTTP proxy. So SOCKS
 * covers browsing and leaves playback direct, and HTTP covers both. That is a genuinely surprising
 * split and the worst possible place to find it out is halfway through an episode, so the note
 * under the picker changes with the choice.
 */
object AnimatoProxyScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.pref_proxy_title

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences = remember { Injekt.get<ProxyPreferences>() }
        val enabled by preferences.enabled.collectAsState()
        val kind by preferences.kind.collectAsState()
        val host by preferences.host.collectAsState()
        val port by preferences.port.collectAsState()
        val username by preferences.username.collectAsState()
        val password by preferences.password.collectAsState()
        val xrayLink by preferences.xrayLink.collectAsState()
        val xrayStatus by XrayController.status.collectAsState()
        val isXray = kind == ProxyKind.Xray

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = preferences.enabled,
                title = stringResource(AYMR.strings.pref_proxy_enabled),
                subtitle = stringResource(AYMR.strings.pref_proxy_enabled_summary),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_proxy_title),
                // Greyed rather than hidden while the switch is off: a form that vanishes takes the
                // settings with it as far as anyone can tell, and these are values people keep and
                // toggle rather than retype.
                visible = enabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.kind,
                        entries = persistentMapOfKinds(),
                        title = stringResource(AYMR.strings.pref_proxy_kind),
                    ),
                    // Built in: one link instead of an address and a login. See XrayController.
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.xrayLink,
                        title = stringResource(AYMR.strings.pref_proxy_xray_link),
                        // The server's name, never the link: it carries the login, and this is the
                        // screen people hand to somebody else to look at.
                        subtitle = xrayLinkSummary(xrayLink),
                        visible = isXray,
                    ),
                    // The core's own answer, so a link that reads fine but will not connect says so.
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_proxy_xray_status),
                        subtitle = xrayStatusText(xrayStatus),
                        visible = isXray && xrayLink.isNotBlank(),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.host,
                        title = stringResource(AYMR.strings.pref_proxy_host),
                        subtitle = host.ifBlank { stringResource(AYMR.strings.pref_proxy_host_summary) },
                        visible = !isXray,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.port,
                        title = stringResource(AYMR.strings.pref_proxy_port),
                        subtitle = port.ifBlank { stringResource(AYMR.strings.pref_proxy_not_set) },
                        visible = !isXray,
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(
                            when (kind) {
                                ProxyKind.Socks5 -> AYMR.strings.pref_proxy_scope_socks
                                ProxyKind.Http -> AYMR.strings.pref_proxy_scope_http
                                ProxyKind.Xray -> AYMR.strings.pref_proxy_scope_xray
                            },
                        ),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_proxy_username),
                // Xray's login is inside its link.
                visible = enabled && !isXray,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.username,
                        title = stringResource(AYMR.strings.pref_proxy_username),
                        subtitle = username.ifBlank { stringResource(AYMR.strings.pref_proxy_not_set) },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.password,
                        title = stringResource(AYMR.strings.pref_proxy_password),
                        // Never the value. A settings list is the one screen people hand to
                        // somebody else to look at, and a subtitle is not a password field.
                        subtitle = stringResource(
                            if (password.isEmpty()) {
                                AYMR.strings.pref_proxy_not_set
                            } else {
                                AYMR.strings.pref_proxy_password_set
                            },
                        ),
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(AYMR.strings.pref_proxy_credentials_summary),
                    ),
                ).addAll(httpLoginWarning(kind, username, password)).toPersistentList(),
            ),
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_proxy_not_a_vpn),
            ),
        )
    }

    /**
     * The one combination that half works, said out loud.
     *
     * A SOCKS proxy's login is handled below OkHttp, inside the socket, through the JVM's
     * authenticator — which this app can reach. An HTTP proxy's is handled by OkHttp's own
     * `proxyAuthenticator`, which is set on the client builder in Mihon's `NetworkHelper` and is not
     * ours to change. So the credentials reach mpv, which takes them in its proxy URL, and reach
     * nothing else.
     *
     * On a device that fails as *every request refused while playback works*, which is a shape
     * nobody would guess at from either half. Shown only once somebody has actually typed a
     * credential under HTTP, because until then it is a warning about nothing.
     */
    @Composable
    private fun httpLoginWarning(
        kind: ProxyKind,
        username: String,
        password: String,
    ): List<Preference.PreferenceItem.InfoPreference> {
        if (kind != ProxyKind.Http) return emptyList()
        if (username.isBlank() && password.isEmpty()) return emptyList()
        return listOf(
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_proxy_http_login_warning),
            ),
        )
    }

    @Composable
    private fun persistentMapOfKinds(): Map<ProxyKind, String> = mapOf(
        ProxyKind.Socks5 to stringResource(AYMR.strings.pref_proxy_kind_socks5),
        ProxyKind.Http to stringResource(AYMR.strings.pref_proxy_kind_http),
        ProxyKind.Xray to stringResource(AYMR.strings.pref_proxy_kind_xray),
    )

    /** What the link is, read back: the server's name and protocol, or why it cannot be read. */
    @Composable
    private fun xrayLinkSummary(link: String): String {
        if (link.isBlank()) return stringResource(AYMR.strings.pref_proxy_xray_link_summary)
        return runCatching { XrayLink.parse(link) }
            .map {
                "${it.name} · ${it.protocol.name.lowercase()}" +
                    if (it.security is XrayLink.Security.Reality) " · reality" else ""
            }
            .getOrElse { it.message.orEmpty() }
    }

    @Composable
    private fun xrayStatusText(status: XrayController.Status): String = when (status) {
        XrayController.Status.Off -> stringResource(AYMR.strings.pref_proxy_xray_off)
        XrayController.Status.Starting -> stringResource(AYMR.strings.pref_proxy_xray_starting)
        is XrayController.Status.Running -> stringResource(AYMR.strings.pref_proxy_xray_running, status.serverName)
        is XrayController.Status.Failed -> stringResource(AYMR.strings.pref_proxy_xray_failed, status.reason)
    }
}
