package animato.app.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionUnusedTest {

    private val cutoff = 1_000L

    @Test
    fun `unused when every source was last opened before the cutoff`() {
        isUnused(listOf(1L, 2L), emptySet(), cutoff) { 500L } shouldBe true
    }

    @Test
    fun `kept when any source was opened after the cutoff`() {
        isUnused(listOf(1L, 2L), emptySet(), cutoff) { if (it == 2L) 2_000L else 500L } shouldBe false
    }

    @Test
    fun `kept when a source has something in the library`() {
        isUnused(listOf(1L, 2L), setOf(2L), cutoff) { 500L } shouldBe false
    }

    @Test
    fun `kept when no source was ever opened`() {
        isUnused(listOf(1L), emptySet(), cutoff) { 0L } shouldBe false
    }

    @Test
    fun `kept when it serves no source`() {
        isUnused(emptyList(), emptySet(), cutoff) { 500L } shouldBe false
    }
}
