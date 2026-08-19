package animato.anime.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The stored form of a per-anime subtitle offset.
 *
 * The map goes through a string and back on every read, so the failures worth catching are the
 * quiet ones: a negative offset — which is half of all real corrections, since subtitles run early
 * as often as late — surviving the round trip, and a malformed entry costing one anime its offset
 * rather than costing every anime theirs.
 */
@Execution(ExecutionMode.CONCURRENT)
class SubtitleDelayMemoryTest {

    @Test
    fun `offsets survive the round trip in both directions`() {
        val delays = mapOf(1L to 2000, 7L to -1500)

        val restored = SubtitleDelayMemory.deserialize(SubtitleDelayMemory.serialize(delays))

        // Negative is not an edge case here: a subtitle file that runs ahead needs one.
        restored shouldBe delays
    }

    @Test
    fun `an empty store reads as no offsets rather than as a broken one`() {
        SubtitleDelayMemory.deserialize("") shouldBe emptyMap()
        SubtitleDelayMemory.serialize(emptyMap()) shouldBe ""
    }

    @Test
    fun `one unreadable entry costs one anime, not all of them`() {
        // Written by an older build, or truncated: the rest of the library keeps its corrections.
        SubtitleDelayMemory.deserialize("1:500,broken,9:250,x:y") shouldBe mapOf(1L to 500, 9L to 250)
    }
}
