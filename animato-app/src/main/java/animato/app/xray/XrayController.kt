package animato.app.xray

import animato.anime.net.ProxyKind
import animato.anime.net.ProxyPreferences
import animato.anime.net.xray.XrayConfig
import animato.anime.net.xray.XrayLink
import animato.anime.net.xray.XraySubscription
import animato.xray.libxray.Libxray
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Headers
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.Proxy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

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

    /** Why the last subscription refresh failed, or null. */
    private val _subscriptionError = MutableStateFlow<String?>(null)
    val subscriptionError: StateFlow<String?> = _subscriptionError.asStateFlow()

    @Synchronized
    fun watch() {
        if (watching) return
        watching = true
        val preferences = Injekt.get<ProxyPreferences>()
        scope.launch {
            // Fetched again when the address changes, and every few hours while it stays the same,
            // so servers changed on the far end arrive without anything being pasted again
            combine(
                preferences.enabled.changes(),
                preferences.kind.changes(),
                preferences.xraySubscription.changes(),
            ) { enabled, kind, address -> address.trim().takeIf { enabled && kind == ProxyKind.Xray } }
                .distinctUntilChanged()
                .collectLatest { address ->
                    if (address.isNullOrBlank()) return@collectLatest
                    while (true) {
                        val fresh = preferences.xraySubscriptionSource.get() == address &&
                            System.currentTimeMillis() - preferences.xraySubscriptionUpdatedAt.get() <
                            SUBSCRIPTION_REFRESH.inWholeMilliseconds
                        if (!fresh) refreshSubscription()
                        delay(SUBSCRIPTION_REFRESH)
                    }
                }
        }
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

    /**
     * Fetches the subscription's server list and keeps the running server when it is still listed.
     * Directly, not through the proxy: the proxy may be the thing a stale list has broken.
     */
    suspend fun refreshSubscription(): Boolean = withContext(Dispatchers.IO) {
        val preferences = Injekt.get<ProxyPreferences>()
        val address = preferences.xraySubscription.get().trim()
        if (address.isBlank()) return@withContext false
        try {
            val client = Injekt.get<NetworkHelper>().client.newBuilder().proxy(Proxy.NO_PROXY).build()
            // The name subscription servers send the standard share-link list to
            val request = GET(address, Headers.headersOf("User-Agent", "v2rayNG"))
            val body = client.newCall(request).awaitSuccess().use { it.body.string() }
            val links = XraySubscription.parse(body)
            if (links.isEmpty()) error("The subscription lists no servers this app can use")

            preferences.xraySubscriptionLinks.set(links.joinToString("\n"))
            preferences.xraySubscriptionSource.set(address)
            preferences.xraySubscriptionUpdatedAt.set(System.currentTimeMillis())
            XraySubscription.reselect(preferences.xrayLink.get(), links)?.let(preferences.xrayLink::set)
            _subscriptionError.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Subscription refresh failed" }
            _subscriptionError.value = e.message ?: e.javaClass.simpleName
            false
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

    private val SUBSCRIPTION_REFRESH = 12.hours
}
