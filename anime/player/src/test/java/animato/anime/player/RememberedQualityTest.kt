package animato.anime.player

import eu.kanade.tachiyomi.animesource.model.Video
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@Execution(ExecutionMode.CONCURRENT)
class RememberedQualityTest {

    @Test
    fun `remembers what was chosen, per anime`() {
        val quality = RememberedQuality(InMemoryPreferenceStore())

        quality.set(1L, video(1080))
        quality.set(2L, video(720))

        quality.get(1L) shouldBe 1080
        quality.get(2L) shouldBe 720
        quality.get(3L).shouldBeNull()
    }

    @Test
    fun `a video with no resolution is not remembered as one`() {
        // Some sources report no resolution at all. Storing a null would be storing a choice that
        // can never match anything next time.
        val quality = RememberedQuality(InMemoryPreferenceStore())

        quality.set(1L, video(null))

        quality.get(1L).shouldBeNull()
    }

    @Test
    fun `choosing again replaces the earlier choice`() {
        val quality = RememberedQuality(InMemoryPreferenceStore())

        quality.set(1L, video(1080))
        quality.set(1L, video(480))

        quality.get(1L) shouldBe 480
    }

    @Test
    fun `the stored form survives a round trip`() {
        // The map lives in a preference as one string, so this is what actually happens between one
        // episode and the next. Tested on the pair directly rather than through the store, because
        // InMemoryPreferenceStore hands out a fresh Preference on every call and remembers only
        // what it was constructed with — a `set` there would be written to an object nothing reads.
        val original = mapOf(1L to 1080, 2L to 720, 3L to 480)

        RememberedQuality.deserialize(RememberedQuality.serialize(original)) shouldBe original
    }

    @Test
    fun `a stored form that got corrupted is skipped rather than thrown on`() {
        // A preference is a string on disk and this is the one place a bad one would surface. The
        // readable entries survive; the rest are dropped.
        RememberedQuality.deserialize("1:1080,nonsense,2:,:720,3:480") shouldBe mapOf(1L to 1080, 3L to 480)
    }

    @Test
    fun `an empty stored form is an empty map`() {
        RememberedQuality.deserialize("") shouldBe emptyMap()
    }

    @Test
    fun `forgetting removes only that anime`() {
        val quality = RememberedQuality(InMemoryPreferenceStore())
        quality.set(1L, video(1080))
        quality.set(2L, video(720))

        quality.forget(1L)

        quality.get(1L).shouldBeNull()
        quality.get(2L) shouldBe 720
    }

    @Test
    fun `the map does not grow without limit`() {
        val quality = RememberedQuality(InMemoryPreferenceStore())

        repeat(600) { quality.set(it.toLong(), video(1080)) }

        // The oldest are dropped, the newest kept. Losing one costs a single tap; an unbounded
        // preference string does not stay cheap on a library of thousands.
        quality.get(0L).shouldBeNull()
        quality.get(599L) shouldBe 1080
    }

    private fun video(resolution: Int?) = Video(
        videoUrl = "https://example.invalid/video",
        videoTitle = resolution?.let { "${it}p" }.orEmpty(),
        resolution = resolution,
    )
}
