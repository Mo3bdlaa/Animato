package animato.app.xray

import animato.anime.net.ProxyKind
import animato.anime.net.ProxyPreferences
import animato.anime.net.xray.XrayConfig
import animato.anime.net.xray.XrayLink
import animato.xray.libxray.Libxray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Keeps the built-in Xray core in step with the proxy settings.
 *
 * Xray runs inside this process, as a library: the Go side (animato-xray) is handed the JSON that
 * [XrayConfig] builds and listens on two loopback ports. Nothing here touches the network itself —
 * the app reaches the internet through that local proxy because [ProxyPreferences.proxy] answers
 * with it, the same way it answers with any proxy somebody typed in.
 *
 * Started once per process, from the same place the rest of the anime side starts up, and then
 * driven entirely by the three preferences that decide it: on, kind, link. Change the link and the
 * core restarts with the new server; switch the kind away and it stops.
 */
object XrayController {

    sealed interface Status {
        data object Off : Status
        data object Starting : Status
        data class Running(val serverName: String) : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Off)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var watching = false

    @Synchronized
    fun watch() {
        if (watching) return
        watching = true
        val preferences = Injekt.get<ProxyPreferences>()
        scope.launch {
            combine(
                preferences.enabled.changes(),
                preferences.kind.changes(),
                preferences.xrayLink.changes(),
            ) { enabled, kind, link -> link.takeIf { enabled && kind == ProxyKind.Xray && it.isNotBlank() } }
                .distinctUntilChanged()
                .collectLatest { link -> apply(link) }
        }
    }

    private fun apply(link: String?) {
        if (link == null) {
            runCatching { Libxray.stop() }
            _status.value = Status.Off
            return
        }
        _status.value = Status.Starting
        _status.value = try {
            val parsed = XrayLink.parse(link)
            Libxray.start(XrayConfig.build(parsed))
            logcat(LogPriority.INFO) { "Xray ${Libxray.version()} running for ${parsed.name}" }
            Status.Running(parsed.name)
        } catch (e: Throwable) {
            // A link that cannot be read and a core that will not start are both reported the same
            // way: on the settings screen, in the sentence the failure came with.
            logcat(LogPriority.WARN, e) { "Xray did not start" }
            runCatching { Libxray.stop() }
            Status.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
}
