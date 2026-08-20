package animato.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import animato.anime.net.ProxyKind
import animato.anime.net.ProxyPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import kotlinx.collections.immutable.persistentListOf
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
                enabled = enabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.kind,
                        entries = persistentMapOfKinds(),
                        title = stringResource(AYMR.strings.pref_proxy_kind),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.host,
                        title = stringResource(AYMR.strings.pref_proxy_host),
                        subtitle = host.ifBlank { stringResource(AYMR.strings.pref_proxy_host_summary) },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        preference = preferences.port,
                        title = stringResource(AYMR.strings.pref_proxy_port),
                        subtitle = port.ifBlank { stringResource(AYMR.strings.pref_proxy_not_set) },
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(
                            when (kind) {
                                ProxyKind.Socks5 -> AYMR.strings.pref_proxy_scope_socks
                                ProxyKind.Http -> AYMR.strings.pref_proxy_scope_http
                            },
                        ),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_proxy_username),
                enabled = enabled,
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
                ),
            ),
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_proxy_not_a_vpn),
            ),
        )
    }

    @Composable
    private fun persistentMapOfKinds(): Map<ProxyKind, String> = mapOf(
        ProxyKind.Socks5 to stringResource(AYMR.strings.pref_proxy_kind_socks5),
        ProxyKind.Http to stringResource(AYMR.strings.pref_proxy_kind_http),
    )
}
