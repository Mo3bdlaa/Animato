package animato.anime.player.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.Immutable
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/** A receiver on this network, ready to be cast to. */
@Immutable
data class CastDevice(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * Televisions on this network, as they appear and disappear.
 *
 * ## Why NsdManager and not a bundled mDNS stack
 *
 * The platform has a resolver, it is the one the rest of the device already uses, and it holds the
 * multicast lock itself — which is the part a bundled library gets wrong on Android, because
 * multicast is off by default to save battery and a library that does not ask for the lock finds
 * nothing on half of all phones and is impossible to debug on the other half.
 *
 * ## Discover, then resolve, one at a time
 *
 * mDNS discovery gives a *name*; the address requires a second round trip. Below API 34 `NsdManager`
 * has a single-slot resolver — a second `resolveService` while one is in flight fails the first with
 * `FAILURE_ALREADY_ACTIVE` — so resolutions are queued rather than fired as they arrive. On a
 * network with four receivers, firing them in parallel is how three of them silently never appear.
 *
 * ## What this does not do
 *
 * It does not remember devices between launches. A receiver that is not answering mDNS right now is
 * a receiver that cannot be cast to right now, and a list containing a television somebody unplugged
 * last week is worse than a shorter honest one.
 */
class FCastDiscovery(private val context: Context) {

    /**
     * Emits the current set of receivers, changing as the network does.
     *
     * A flow rather than a callback so that discovery stops when the screen showing it goes away —
     * mDNS discovery keeps a socket and a wake path open, and leaving it running because a sheet
     * was dismissed is a battery cost nobody asked for.
     */
    fun devices(): Flow<List<CastDevice>> = callbackFlow {
        val manager = context.getSystemService<NsdManager>()
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val found = LinkedHashMap<String, CastDevice>()
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            if (resolving) return
            val next = pending.removeFirstOrNull() ?: return
            resolving = true

            manager.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        logcat(LogPriority.DEBUG) { "FCast: could not resolve ${info.serviceName} ($errorCode)" }
                        resolving = false
                        resolveNext()
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val address = info.host?.hostAddress
                        if (address != null) {
                            found[info.serviceName] = CastDevice(
                                name = info.serviceName,
                                host = address,
                                // A receiver is free to advertise another port and some do.
                                port = info.port.takeIf { it > 0 } ?: FCastProtocol.PORT,
                            )
                            trySend(found.values.toList())
                        }
                        resolving = false
                        resolveNext()
                    }
                },
            )
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                pending.addLast(info)
                resolveNext()
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                if (found.remove(info.serviceName) != null) {
                    trySend(found.values.toList())
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logcat(LogPriority.WARN) { "FCast: discovery would not start ($errorCode)" }
                trySend(emptyList())
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        manager.discoverServices(
            FCastProtocol.SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            listener,
        )

        awaitClose {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
    }
}
