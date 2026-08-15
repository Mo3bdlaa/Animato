package animato.app.updater

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class AnimatoAppUpdateCheckerTest {

    private val arm64 = listOf("arm64-v8a", "armeabi-v7a")
    private val arm32 = listOf("armeabi-v7a")

    @Test
    fun `offers the newest release`() {
        val result = newerRelease(
            releases = listOf(release("v0.1.0-alpha.5"), release("v0.1.0-alpha.7"), release("v0.1.0-alpha.6")),
            installed = installed("0.1.0-alpha.5"),
            abis = arm64,
        )

        result!!.version shouldBe "0.1.0-alpha.7"
        result.downloadLink shouldBe "https://example.invalid/animato-v0.1.0-alpha.7-arm64-v8a.apk"
    }

    @Test
    fun `offers nothing when the running build is the newest`() {
        newerRelease(
            releases = listOf(release("v0.1.0-alpha.7")),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        ).shouldBeNull()
    }

    @Test
    fun `an alpha build is offered alphas`() {
        newerRelease(
            releases = listOf(release("v0.1.0-alpha.8", isPrerelease = true)),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        )!!.version shouldBe "0.1.0-alpha.8"
    }

    @Test
    fun `a finished build is not dragged onto an alpha`() {
        // 0.2.0-alpha.1 outranks 0.1.0 by semver, and is still not offered: whoever installed a
        // finished release did not ask for prereleases.
        newerRelease(
            releases = listOf(release("v0.2.0-alpha.1", isPrerelease = true)),
            installed = installed("0.1.0"),
            abis = arm64,
        ).shouldBeNull()

        newerRelease(
            releases = listOf(release("v0.2.0", isPrerelease = false)),
            installed = installed("0.1.0"),
            abis = arm64,
        )!!.version shouldBe "0.2.0"
    }

    @Test
    fun `drafts are not releases`() {
        newerRelease(
            releases = listOf(release("v0.1.0-alpha.8", isDraft = true)),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        ).shouldBeNull()
    }

    @Test
    fun `a tag that is not a version is skipped rather than guessed at`() {
        val result = newerRelease(
            releases = listOf(release("nightly"), release("v0.1.0-alpha.8")),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        )

        result!!.version shouldBe "0.1.0-alpha.8"
    }

    @Test
    fun `the device's preferred architecture wins over one it merely supports`() {
        newerRelease(
            releases = listOf(release("v0.1.0-alpha.8")),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        )!!.downloadLink shouldBe "https://example.invalid/animato-v0.1.0-alpha.8-arm64-v8a.apk"

        newerRelease(
            releases = listOf(release("v0.1.0-alpha.8")),
            installed = installed("0.1.0-alpha.7"),
            abis = arm32,
        )!!.downloadLink shouldBe "https://example.invalid/animato-v0.1.0-alpha.8-armeabi-v7a.apk"
    }

    @Test
    fun `x86 does not match the x86_64 file`() {
        val result = newerRelease(
            releases = listOf(release("v0.1.0-alpha.8", abis = listOf("x86_64"))),
            installed = installed("0.1.0-alpha.7"),
            abis = listOf("x86"),
        )

        result.shouldBeNull()
    }

    @Test
    fun `a release with nothing this device can install falls through to one that has something`() {
        val result = newerRelease(
            releases = listOf(
                release("v0.1.0-alpha.9", abis = listOf("x86_64")),
                release("v0.1.0-alpha.8", abis = listOf("arm64-v8a")),
            ),
            installed = installed("0.1.0-alpha.7"),
            abis = arm64,
        )

        result!!.version shouldBe "0.1.0-alpha.8"
    }

    private fun installed(raw: String) = SemanticVersion.parse(raw)!!

    private fun release(
        tag: String,
        isPrerelease: Boolean = true,
        isDraft: Boolean = false,
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
    ) = GithubReleaseSummary(
        tag = tag,
        info = "Alpha build.",
        releaseLink = "https://example.invalid/releases/tag/$tag",
        isDraft = isDraft,
        isPrerelease = isPrerelease,
        assets = abis.map { abi ->
            GithubReleaseAsset(
                name = "animato-$tag-$abi.apk",
                downloadLink = "https://example.invalid/animato-$tag-$abi.apk",
            )
        },
    )
}
