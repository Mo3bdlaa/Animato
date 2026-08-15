package animato.app.updater

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SemanticVersionTest {

    @Test
    fun `reads a release tag`() {
        val version = SemanticVersion.parse("v0.1.0-alpha.7")!!

        version.numbers shouldBe listOf(0, 1, 0)
        version.prerelease shouldBe listOf("alpha", "7")
        version.isPrerelease shouldBe true
        version.toString() shouldBe "0.1.0-alpha.7"
    }

    @Test
    fun `build metadata is not part of the version`() {
        SemanticVersion.parse("1.2.3+2026.08.15") shouldBe SemanticVersion.parse("1.2.3")
    }

    @Test
    fun `a tag that is not a version is not one`() {
        SemanticVersion.parse("nightly").shouldBeNull()
        SemanticVersion.parse("v").shouldBeNull()
        SemanticVersion.parse("").shouldBeNull()
        SemanticVersion.parse("1.2.beta").shouldBeNull()
        // An empty identifier, which semver forbids.
        SemanticVersion.parse("1.2.3-alpha..1").shouldBeNull()
    }

    @Test
    fun `numbers are compared as numbers, not as text`() {
        // The one that string ordering gets wrong.
        (version("0.1.10") > version("0.1.9")) shouldBe true
        (version("0.2.0") > version("0.1.99")) shouldBe true
        (version("1.0.0") > version("0.99.99")) shouldBe true
    }

    @Test
    fun `a missing number counts as zero`() {
        // Equal in order, not the same value: the version keeps the shape it was written in so it
        // can be shown back as itself, and only the comparison pads.
        version("1.2").compareTo(version("1.2.0")) shouldBe 0
        (version("1.2.1") > version("1.2")) shouldBe true
    }

    @Test
    fun `an alpha is older than the release it leads to`() {
        // The case Mihon's comparison cannot express, and the reason this class exists.
        (version("0.1.0-alpha.7") < version("0.1.0")) shouldBe true
        (version("0.1.0") > version("0.1.0-alpha.7")) shouldBe true
    }

    @Test
    fun `alphas are ordered among themselves`() {
        (version("0.1.0-alpha.7") > version("0.1.0-alpha.6")) shouldBe true
        // Ten is not "less than nine" here, which it would be as text.
        (version("0.1.0-alpha.10") > version("0.1.0-alpha.9")) shouldBe true
        (version("0.1.0-beta.1") > version("0.1.0-alpha.99")) shouldBe true
        // A numeric identifier ranks below an alphanumeric one, and more identifiers rank above
        // fewer when everything before them matches.
        (version("1.0.0-1") < version("1.0.0-alpha")) shouldBe true
        (version("1.0.0-alpha.1") > version("1.0.0-alpha")) shouldBe true
    }

    @Test
    fun `the whole semver precedence example orders correctly`() {
        val expected = listOf(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
        )

        expected.shuffled().map(::version).sorted().map(SemanticVersion::toString) shouldBe expected
    }

    private fun version(raw: String) = SemanticVersion.parse(raw)!!
}
