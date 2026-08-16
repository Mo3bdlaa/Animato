package animato.anime.player.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** Where a cast session has got to, as far as this device can tell. */
sealed interface CastState {
    data object Disconnected : CastState
    data object Connecting : CastState
    data class Connected(val deviceName: String) : CastState
    data class Playing(
        val deviceName: String,
        val position: Double,
        val duration: Double,
        val paused: Boolean,
    ) : CastState

    data class Failed(val reason: String) : CastState
}

/**
 * One connection to one receiver.
 *
 * ## Two threads and a queue, not a lock
 *
 * FCast is full duplex: the receiver pushes playback updates whenever it likes while the sender is
 * still writing commands. Both ends of the socket are therefore held open at once, one coroutine
 * reading and one writing, with an unbounded channel between the caller and the writer.
 *
 * The alternative — synchronising on the output stream from whichever thread pressed pause — is the
 * arrangement where the read loop's blocking `read` holds a lock the writer wants, and the app stops
 * responding to its own pause button while a television thinks about something else.
 *
 * ## Why failures are a state and not an exception
 *
 * A television being switched off mid-episode is ordinary. There is nothing to catch and nothing to
 * retry: the session ends, the state says why in words, and the UI puts the cast button back. Every
 * socket path here therefore funnels into [fail] rather than out to a caller.
 */
internal class FCastSession(
    private val host: String,
    private val deviceName: String,
) {

    private val _state = MutableStateFlow<CastState>(CastState.Disconnected)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val outbox = Channel<Pair<FCastOpcode, String?>>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)

    private var socket: Socket? = null
    private var pump: Job? = null

    /**
     * Opens the connection and announces the version.
     *
     * The connect timeout is short on purpose: this runs while somebody is looking at a list of
     * televisions waiting for one of them to light up, and a device that has been unplugged since
     * mDNS last saw it should fail in seconds rather than in the platform's default minute.
     */
    fun connect() {
        _state.value = CastState.Connecting

        pump = scope.launch {
            try {
                val connection = Socket().apply {
                    connect(InetSocketAddress(host, FCastProtocol.PORT), CONNECT_TIMEOUT_MILLIS)
                    // No read timeout: silence from a receiver playing a long episode is normal, and
                    // a timeout here would tear down a session that is working perfectly.
                    soTimeout = 0
                    tcpNoDelay = true
                }
                socket = connection

                val output = connection.getOutputStream()
                val input = connection.getInputStream()

                FCastProtocol.write(
                    output,
                    FCastOpcode.Version,
                    FCastProtocol.json.encodeToString(VersionMessage()),
                )
                _state.value = CastState.Connected(deviceName)

                launch { writeLoop(output) }
                readLoop(input)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private suspend fun writeLoop(output: java.io.OutputStream) {
        try {
            for ((opcode, body) in outbox) {
                FCastProtocol.write(output, opcode, body)
            }
        } catch (e: IOException) {
            fail(e)
        }
    }

    private fun readLoop(input: java.io.InputStream) {
        while (!closed.get()) {
            val packet = try {
                FCastProtocol.read(input)
            } catch (e: EOFException) {
                // The receiver hung up. That is how a session normally ends.
                close()
                return
            } catch (e: IOException) {
                fail(e)
                return
            } ?: continue

            when (packet.opcode) {
                FCastOpcode.PlaybackUpdate -> onPlaybackUpdate(packet.body)
                FCastOpcode.PlaybackError -> onPlaybackError(packet.body)
                // The receiver asks; answering is the whole of it. A sender that ignores Ping is
                // dropped by some receivers after a minute of an episode playing fine.
                FCastOpcode.Ping -> send(FCastOpcode.Pong)
                else -> Unit
            }
        }
    }

    private fun onPlaybackUpdate(body: String) {
        val update = runCatching {
            FCastProtocol.json.decodeFromString<PlaybackUpdateMessage>(body)
        }.getOrNull() ?: return

        val name = deviceName
        _state.value = when (update.state) {
            STATE_IDLE -> CastState.Connected(name)
            else -> CastState.Playing(
                deviceName = name,
                position = update.time,
                duration = update.duration,
                paused = update.state == STATE_PAUSED,
            )
        }
    }

    private fun onPlaybackError(body: String) {
        val message = runCatching {
            FCastProtocol.json.decodeFromString<PlaybackErrorMessage>(body).message
        }.getOrNull()
        // The receiver's own words. It is the only party that knows why its decoder refused.
        _state.value = CastState.Failed(message ?: UNKNOWN_RECEIVER_ERROR)
    }

    fun play(url: String, container: String, positionSeconds: Double) = send(
        FCastOpcode.Play,
        FCastProtocol.json.encodeToString(
            PlayMessage(container = container, url = url, time = positionSeconds),
        ),
    )

    fun pause() = send(FCastOpcode.Pause)

    fun resume() = send(FCastOpcode.Resume)

    fun stop() = send(FCastOpcode.Stop)

    fun seek(seconds: Double) = send(
        FCastOpcode.Seek,
        FCastProtocol.json.encodeToString(SeekMessage(seconds)),
    )

    fun setSpeed(speed: Double) = send(
        FCastOpcode.SetSpeed,
        FCastProtocol.json.encodeToString(SetSpeedMessage(speed)),
    )

    private fun send(opcode: FCastOpcode, body: String? = null) {
        if (closed.get()) return
        outbox.trySend(opcode to body)
    }

    /**
     * Ends the session.
     *
     * `Stop` is *not* sent here. Closing the phone's session and stopping the television are
     * different intentions — walking away from a cast should leave the episode playing on the
     * television, which is what casting is for. [stop] exists for the other one.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        outbox.close()
        runCatching { socket?.close() }
        pump?.cancel()
        _state.value = CastState.Disconnected
    }

    private fun fail(e: Throwable) {
        logcat(LogPriority.WARN, e) { "FCast session to $deviceName failed" }
        if (closed.compareAndSet(false, true)) {
            outbox.close()
            runCatching { socket?.close() }
        }
        _state.value = CastState.Failed(e.message ?: e::class.simpleName ?: UNKNOWN_RECEIVER_ERROR)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000

        /** The protocol numbers playback states rather than naming them. */
        const val STATE_IDLE = 0
        const val STATE_PAUSED = 2

        const val UNKNOWN_RECEIVER_ERROR = "The receiver did not say why"
    }
}
