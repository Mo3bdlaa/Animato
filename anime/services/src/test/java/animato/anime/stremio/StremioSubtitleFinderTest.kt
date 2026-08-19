package animato.anime.stremio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The two judgements the bridge makes before it will claim an id.
 *
 * Both are worth a test because both fail quietly. A title comparison that is too strict finds
 * nothing and looks like the feature is off; one that is too loose puts the wrong show's subtitles
 * over the video, which is worse than none. And a season read wrongly out of a title returns
 * subtitles for the right show and the wrong episode — the failure that is hardest to recognise as
 * a failure.
 */
@Execution(ExecutionMode.CONCURRENT)
class StremioSubtitleFinderTest {

    @Test
    fun `the same show spelled by two sites compares equal`() {
        // Left: an extension's scrape. Right: what IMDb calls it.
        StremioSubtitleFinder.normalise("Frieren: Beyond Journey's End") shouldBe
            StremioSubtitleFinder.normalise("Frieren Beyond Journeys End")

        StremioSubtitleFinder.normalise("JUJUTSU KAISEN") shouldBe
            StremioSubtitleFinder.normalise("Jujutsu Kaisen")
    }

    @Test
    fun `two different shows do not`() {
        val one = StremioSubtitleFinder.normalise("Solo Leveling")
        val other = StremioSubtitleFinder.normalise("Solo Leveling: Ragnarok")
        (one == other) shouldBe false
    }

    @Test
    fun `a season is read from the title however the site spells it`() {
        StremioSubtitleFinder.seasonIn("Attack on Titan Season 2") shouldBe 2
        StremioSubtitleFinder.seasonIn("Mob Psycho 100 II 2nd Season") shouldBe 2
        StremioSubtitleFinder.seasonIn("Vinland Saga S2") shouldBe 2
        StremioSubtitleFinder.seasonIn("Bocchi the Rock! 3rd Season") shouldBe 3
    }

    @Test
    fun `a title with no season is the first one`() {
        // Which is what an unmarked anime title almost always is.
        StremioSubtitleFinder.seasonIn("Frieren: Beyond Journey's End") shouldBe 1
        StremioSubtitleFinder.seasonIn("Solo Leveling") shouldBe 1
    }

    @Test
    fun `a number in the name is not mistaken for a season`() {
        // The failure this guards: "86" or "91 Days" reading as season 86.
        StremioSubtitleFinder.seasonIn("86 EIGHTY-SIX") shouldBe 1
        StremioSubtitleFinder.seasonIn("Mob Psycho 100") shouldBe 1
    }
}
