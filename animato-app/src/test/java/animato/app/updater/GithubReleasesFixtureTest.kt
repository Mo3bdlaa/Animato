package animato.app.updater

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The updater against what GitHub actually answers with.
 *
 * `AnimatoAppUpdateCheckerTest` covers the ordering rules with releases this test file made up,
 * which proves the logic and proves nothing about the parsing. This one runs the same code over a
 * response captured verbatim from `api.github.com/repos/Mo3bdlaa/Animato/releases` — nulls, bot
 * authors, unknown keys and all — so a field that GitHub spells differently than assumed fails here
 * rather than on someone's phone, silently, inside the `catch` the check is wrapped in.
 *
 * That is not a hypothetical failure mode. The updater shipped twice without ever running, and the
 * silence is exactly why it took a user saying "it did not offer me anything" to find out.
 */
@Execution(ExecutionMode.CONCURRENT)
class GithubReleasesFixtureTest {

    /** The same configuration the app injects. */
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val releases: List<GithubReleaseSummary> by lazy {
        val raw = javaClass.getResourceAsStream("/github-releases.json")!!
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString(raw)
    }

    @Test
    fun `the real response parses`() {
        releases.size shouldBe 2
        releases[0].tag shouldBe "v0.1.0-alpha.7"
        releases[0].isPrerelease shouldBe true
        releases[0].isDraft shouldBe false
        releases[0].assets.size shouldBe 4
    }

    @Test
    fun `alpha 6 is offered alpha 7`() {
        val update = newerRelease(
            releases = releases,
            installed = SemanticVersion.parse("0.1.0-alpha.6")!!,
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        update!!.version shouldBe "0.1.0-alpha.7"
        update.downloadLink shouldBe
            "https://github.com/Mo3bdlaa/Animato/releases/download/" +
            "v0.1.0-alpha.7/animato-v0.1.0-alpha.7-arm64-v8a.apk"
        update.releaseLink shouldBe "https://github.com/Mo3bdlaa/Animato/releases/tag/v0.1.0-alpha.7"
    }

    @Test
    fun `alpha 7 is offered nothing`() {
        newerRelease(
            releases = releases,
            installed = SemanticVersion.parse("0.1.0-alpha.7")!!,
            abis = listOf("arm64-v8a"),
        ) shouldBe null
    }

    @Test
    fun `a build that forgot to name its alpha is offered nothing`() {
        // What alpha.5 and earlier called themselves. Plain 0.1.0 outranks every prerelease of it,
        // so it is *correctly* offered nothing — which is the second reason the updater looked
        // broken, and the reason the workflow now passes ANIMATO_VERSION_NAME.
        newerRelease(
            releases = releases,
            installed = SemanticVersion.parse("0.1.0")!!,
            abis = listOf("arm64-v8a"),
        ) shouldBe null
    }
}
