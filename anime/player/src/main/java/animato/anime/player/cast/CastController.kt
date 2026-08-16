package animato.anime.player.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * What is being cast, so a receiver can be handed it.
 *
 * [headerBound] is the one that decides whether casting is possible at all. An extension's video URL
 * may only be fetchable with the headers that extension sends — a referer, a token, a cookie — and a
 * television fetching that URL sends none of them. The local video server exists to re-inject them,
 * but it binds loopback deliberately (see `HttpServer`), and a receiver cannot reach loopback. So a
 * header-bound video is *known* to be uncastable rather than discovered to be by watching a
 * television show a black screen.
 */
data class CastRequest(
    val url: String,
    val container: String,
    val positionSeconds: Double,
    val headerBound: Boolean,
)

/**
 * The one cast session the app has, and everything that can be asked of it.
 *
 * A singleton because a phone casts to one television at a time, and because the session has to
 * outlive the screen that started it: casting is the case where you put the phone down. The player
 * activity being destroyed must not stop the episode.
 *
 * ## What it refuses
 *
 * [cast] declines a header-bound video and says so, rather than connecting and letting the receiver
 * fail. That is not a limitation being hidden — it is the limitation being stated at the only moment
 * anybody can act on it, which is before the television goes black.
 */
class CastController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<CastState>(CastState.Disconnected)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private var session: FCastSession? = null
    private var mirror: Job? = null

    val isCasting: Boolean
        get() = state.value.let { it !is CastState.Disconnected && it !is CastState.Failed }

    /**
     * Connects to [device] and starts [request] on it.
     *
     * Any previous session is closed first — without a `Stop`, because the previous television
     * keeping its episode is the correct outcome of moving to a different one only if somebody
     * asked; here they did not, so the old session is ended and the old receiver stops on its own
     * when its stream ends. Explicit stopping is [stop].
     */
    fun cast(device: CastDevice, request: CastRequest): CastResult {
        if (request.headerBound) return CastResult.NeedsHeaders

        disconnect()

        val newSession = FCastSession(host = device.host, deviceName = device.name)
        session = newSession
        mirror = newSession.state
            .onEach { sessionState ->
                _state.value = sessionState
                // The receiver has accepted the connection; give it something to play.
                if (sessionState is CastState.Connected) {
                    newSession.play(
                        url = request.url,
                        container = request.container,
                        positionSeconds = request.positionSeconds,
                    )
                }
            }
            .launchIn(scope)

        newSession.connect()
        return CastResult.Started
    }

    fun pause() = session?.pause()

    fun resume() = session?.resume()

    fun seek(seconds: Double) = session?.seek(seconds)

    fun setSpeed(speed: Double) = session?.setSpeed(speed)

    /** Stops the receiver as well as the session. The one that means "put it away". */
    fun stop() {
        session?.stop()
        disconnect()
    }

    /** Ends this device's session and leaves the receiver playing. */
    fun disconnect() {
        mirror?.cancel()
        session?.close()
        session = null
        _state.value = CastState.Disconnected
    }
}

/** Whether a cast could be started, and if not, the one reason it could not. */
sealed interface CastResult {
    data object Started : CastResult

    /**
     * The video only loads with headers the receiver cannot send.
     *
     * Fixable, and deliberately not fixed here: it needs the local video server bound to the LAN
     * rather than to loopback, which is a real exposure — an unauthenticated stream readable by
     * anything on the network — and belongs behind an opt-in that says so. `HttpServer` carries the
     * same note at the other end of the same decision.
     */
    data object NeedsHeaders : CastResult
}
