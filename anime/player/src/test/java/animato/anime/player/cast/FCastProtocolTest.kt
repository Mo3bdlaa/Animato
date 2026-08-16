package animato.anime.player.cast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/**
 * The wire format, byte by byte.
 *
 * Nobody here has an FCast receiver in front of them while writing this, so what can be verified is
 * that the bytes match the specification rather than that a television likes them. That is worth
 * doing precisely: every field below was read off the protocol documentation, and the one most
 * likely to be wrong — whether the length counts the opcode — is asserted directly rather than
 * assumed from a round trip that would agree with itself either way.
 */
@Execution(ExecutionMode.CONCURRENT)
class FCastProtocolTest {

    // The same configuration the sender uses, so the test cannot pass on settings the
    // real path does not have — which is exactly how the empty version message survived.
    private val json = FCastProtocol.json

    private fun bytesOf(opcode: FCastOpcode, body: String? = null): ByteArray =
        ByteArrayOutputStream().also { FCastProtocol.write(it, opcode, body) }.toByteArray()

    @Test
    fun `a message with no body is five bytes and declares a size of one`() {
        val bytes = bytesOf(FCastOpcode.Pause)

        bytes.size shouldBe 5
        // The size counts the opcode and not itself. Reading it as the body length would leave a
        // byte in the stream and misalign every packet after it.
        bytes[0] shouldBe 1.toByte()
        bytes[1] shouldBe 0.toByte()
        bytes[2] shouldBe 0.toByte()
        bytes[3] shouldBe 0.toByte()
        bytes[4] shouldBe 2.toByte()
    }

    @Test
    fun `the length is little-endian`() {
        // 300 bytes of body is 301 with the opcode: 0x012D, which is 2D 01 little-endian and would
        // be 01 2D the other way round. A body under 256 bytes cannot tell the two apart.
        val bytes = bytesOf(FCastOpcode.Play, "x".repeat(300))

        bytes[0] shouldBe 0x2D.toByte()
        bytes[1] shouldBe 0x01.toByte()
        bytes[2] shouldBe 0.toByte()
        bytes[3] shouldBe 0.toByte()
    }

    @Test
    fun `a written packet reads back`() {
        val body = json.encodeToString(PlayMessage(container = "video/mp4", url = "http://x/y.mp4"))
        val packet = FCastProtocol.read(ByteArrayInputStream(bytesOf(FCastOpcode.Play, body)))!!

        packet.opcode shouldBe FCastOpcode.Play
        json.decodeFromString<PlayMessage>(packet.body) shouldBe
            PlayMessage(container = "video/mp4", url = "http://x/y.mp4")
    }

    @Test
    fun `an opcode this sender does not model is skipped, not fatal`() {
        // A receiver newer than us will send messages we have no name for. Consuming the packet and
        // returning null keeps the stream aligned; throwing would drop a working connection over a
        // message we did not need.
        val unknown = byteArrayOf(1, 0, 0, 0, 99)

        FCastProtocol.read(ByteArrayInputStream(unknown)).shouldBeNull()
    }

    @Test
    fun `a stream that ends mid-packet is an error rather than a short read`() {
        val truncated = bytesOf(FCastOpcode.Play, "{}").copyOfRange(0, 6)

        shouldThrow<EOFException> { FCastProtocol.read(ByteArrayInputStream(truncated)) }
    }

    @Test
    fun `a body delivered one byte at a time still reads`() {
        // Sockets return short reads whenever they like. A reader that trusts a single `read` call
        // works on a fast local network and fails on a slow one, which is the worst way to be wrong.
        val whole = bytesOf(FCastOpcode.Play, """{"container":"video/mp4","url":"http://x"}""")

        val dribbling = object : InputStream() {
            private var at = 0
            override fun read(): Int = if (at < whole.size) whole[at++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (at >= whole.size) return -1
                b[off] = whole[at++]
                return 1 // one byte, however many were asked for
            }
        }

        FCastProtocol.read(dribbling)!!.opcode shouldBe FCastOpcode.Play
    }

    @Test
    fun `a declared size past the protocol's limit is refused`() {
        val absurd = byteArrayOf(0, 0, 0, 0x40, 1) // 0x40000000 bytes

        shouldThrow<IllegalArgumentException> { FCastProtocol.read(ByteArrayInputStream(absurd)) }
    }

    @Test
    fun `a body over the limit is refused before it is sent`() {
        shouldThrow<IllegalArgumentException> {
            FCastProtocol.write(ByteArrayOutputStream(), FCastOpcode.Play, "x".repeat(32_001))
        }
    }

    @Test
    fun `every opcode maps back from its number`() {
        FCastOpcode.entries.forEach { FCastOpcode.of(it.value) shouldBe it }
    }

    @Test
    fun `the version message says two`() {
        json.encodeToString(VersionMessage()) shouldBe """{"version":2}"""
    }

    @Test
    fun `a playback update decodes from what a receiver sends`() {
        val fromReceiver = """{"generationTime":1690000000,"time":12.5,"duration":1440.0,"state":1,"speed":1.0}"""

        json.decodeFromString<PlaybackUpdateMessage>(fromReceiver) shouldBe PlaybackUpdateMessage(
            generationTime = 1_690_000_000,
            time = 12.5,
            duration = 1440.0,
            state = 1,
            speed = 1.0,
        )
    }

    @Test
    fun `a playback update missing fields still decodes`() {
        // Receivers differ in what they fill in, and a missing field must not take down the
        // connection — the defaults are what make that true.
        json.decodeFromString<PlaybackUpdateMessage>("""{"time":3.0}""").time shouldBe 3.0
    }
}
