package animato.anime.player.cast

import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * The FCast wire format, which is the whole of what talking to a television requires.
 *
 * FCast is the open casting protocol — no Play Services, and it takes a plain URL, which is exactly
 * what the local video server already hands out. That server is why casting is close at all: it
 * re-injects the headers and expiring tokens an extension's URL needs, so what leaves this device is
 * an ordinary URL a receiver can fetch.
 *
 * ## Why this is written out rather than depended on
 *
 * FUTO publish a sender SDK, and it is not a package. The documented JitPack coordinates resolve to
 * nothing — checked, along with four spellings of them, against a JitPack that serves this project's
 * other dependencies perfectly well — and their own instructions say to clone the repository, build
 * the **Rust** binaries and generate the Kotlin bindings through UniFFI. That is a Rust toolchain in
 * CI, cross-compiled per ABI, on every release, plus native libraries in an APK that is already
 * 102 MB and mostly native libraries.
 *
 * Against that: a length, a byte, and some JSON. The protocol is smaller than the machinery for
 * consuming somebody else's copy of it.
 *
 * ## The format
 *
 * ```
 *  0      4        5
 *  +------+--------+-------------------------+
 *  | size | opcode | body (UTF-8 JSON)       |
 *  +------+--------+-------------------------+
 * ```
 *
 * `size` is little-endian and **counts the opcode byte but not itself**, so a message with no body
 * has `size = 1`. That is the detail worth stating twice: reading it as the body length alone leaves
 * one byte in the stream and every packet after it misaligned by one — a failure that looks like
 * corruption rather than an off-by-one.
 */
internal object FCastProtocol {

    /** The port every FCast receiver listens on. */
    const val PORT = 46899

    /** The service type receivers advertise over mDNS. */
    const val SERVICE_TYPE = "_fcast._tcp"

    /**
     * The version this sender claims.
     *
     * Version 2 on purpose. Version 3 adds a mandatory handshake — a `Version` exchange followed by
     * an `Initial` message — and events we have no use for, and the specification says the party
     * with the higher version downgrades. So announcing 2 is understood by a v3 receiver and by a v2
     * one, and adding features later is a change to this constant rather than to the framing.
     */
    const val VERSION = 2L

    /** The receiver refuses anything larger, so there is no point assembling it. */
    const val MAX_BODY_BYTES = 32_000

    /**
     * One configuration, for what is sent and for what arrives.
     *
     * **`encodeDefaults` is the whole point of this existing.** kotlinx.serialization omits any
     * property equal to its default, so `VersionMessage()` — whose only field defaults to 2 —
     * encoded to `{}`. A version announcement carrying no version is not a smaller message, it is
     * the wrong one, and a receiver would have had to infer a version from an empty object. A test
     * caught it; a television would have reported it as a handshake that did not work.
     *
     * `ignoreUnknownKeys` for the other direction: receivers add fields, and a message we can
     * mostly read is worth more than an exception.
     */
    val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private const val HEADER_BYTES = 5

    /**
     * Writes one packet.
     *
     * [body] is the JSON already encoded, or null for the messages that carry none — `Pause`,
     * `Resume`, `Stop` are the whole opcode.
     */
    fun write(output: OutputStream, opcode: FCastOpcode, body: String? = null) {
        val payload = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        require(payload.size <= MAX_BODY_BYTES) {
            "FCast body is ${payload.size} bytes, over the ${MAX_BODY_BYTES} the protocol allows"
        }

        val size = payload.size + 1 // the opcode, not this field
        val packet = ByteArray(HEADER_BYTES + payload.size)
        packet[0] = (size and 0xFF).toByte()
        packet[1] = (size ushr 8 and 0xFF).toByte()
        packet[2] = (size ushr 16 and 0xFF).toByte()
        packet[3] = (size ushr 24 and 0xFF).toByte()
        packet[4] = opcode.value.toByte()
        payload.copyInto(packet, HEADER_BYTES)

        output.write(packet)
        output.flush()
    }

    /**
     * Reads one packet, blocking until it is whole.
     *
     * Returns null for an opcode this sender does not model, having consumed the packet — an unknown
     * message must not desynchronise the stream, and a receiver newer than us will send some.
     *
     * @throws EOFException when the connection closes mid-packet.
     */
    fun read(input: InputStream): FCastPacket? {
        val header = input.readFully(HEADER_BYTES)

        val size = (header[0].toInt() and 0xFF) or
            (header[1].toInt() and 0xFF shl 8) or
            (header[2].toInt() and 0xFF shl 16) or
            (header[3].toInt() and 0xFF shl 24)

        // A size of zero would not even cover the opcode already read, and anything past the cap is
        // a stream that has lost its place. Either way the connection cannot be trusted.
        require(size in 1..(MAX_BODY_BYTES + 1)) { "FCast declared a packet of $size bytes" }

        val body = input.readFully(size - 1)
        val opcode = FCastOpcode.of(header[4].toInt() and 0xFF) ?: return null

        return FCastPacket(opcode, body.toString(Charsets.UTF_8))
    }

    /**
     * Exactly [count] bytes, or an exception.
     *
     * `InputStream.read` is allowed to return fewer bytes than asked for whenever it feels like it,
     * and over a socket it routinely does. Trusting one call is how a protocol reader works on a
     * fast local network and fails on a slow one.
     */
    private fun InputStream.readFully(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = read(buffer, read, count - read)
            if (n < 0) throw EOFException("FCast connection closed after $read of $count bytes")
            read += n
        }
        return buffer
    }
}

internal data class FCastPacket(val opcode: FCastOpcode, val body: String)

/**
 * The messages, by their numbers on the wire.
 *
 * All thirteen of version 2 are here even though this sender uses six, because [FCastProtocol.read]
 * has to be able to name what arrives in order to skip it deliberately rather than by accident.
 */
internal enum class FCastOpcode(val value: Int) {
    Play(1),
    Pause(2),
    Resume(3),
    Stop(4),
    Seek(5),
    PlaybackUpdate(6),
    VolumeUpdate(7),
    SetVolume(8),
    PlaybackError(9),
    SetSpeed(10),
    Version(11),
    Ping(12),
    Pong(13),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun of(value: Int): FCastOpcode? = byValue[value]
    }
}
