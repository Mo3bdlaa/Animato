package animato.domain.content

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * The lens, and the one thing the television build stakes everything on.
 *
 * That build ships without the manga reader's native libraries, so "the lens cannot be moved off
 * anime" is not a preference there — it is the difference between an app and a crash. Worth a test
 * for the same reason the exclusion is worth a size check in the release workflow: both halves have
 * to hold, and a silent failure in either is only discovered by somebody opening a chapter.
 */
class ContentPreferencesTest {

    @Test
    fun `the lens is a choice in an ordinary build`() {
        val preferences = ContentPreferences(InMemoryPreferenceStore())

        preferences.lensIsFixed shouldBe false
        preferences.contentFilter.get() shouldBe ContentFilter.ALL

        preferences.contentFilter.set(ContentFilter.MANGA)
        preferences.contentFilter.get() shouldBe ContentFilter.MANGA
    }

    @Test
    fun `a fixed lens reports the type the build was made for`() {
        val preferences = ContentPreferences(InMemoryPreferenceStore(), fixedTo = ContentFilter.ANIME)

        preferences.lensIsFixed shouldBe true
        preferences.contentFilter.get() shouldBe ContentFilter.ANIME
    }

    @Test
    fun `a fixed lens cannot be written to`() {
        val preferences = ContentPreferences(InMemoryPreferenceStore(), fixedTo = ContentFilter.ANIME)

        preferences.contentFilter.set(ContentFilter.MANGA)
        preferences.contentFilter.set(ContentFilter.ALL)

        preferences.contentFilter.get() shouldBe ContentFilter.ANIME
    }

    @Test
    fun `a fixed lens emits its one value to whoever is watching`() = runTest {
        val preferences = ContentPreferences(InMemoryPreferenceStore(), fixedTo = ContentFilter.ANIME)

        preferences.contentFilter.changes().first() shouldBe ContentFilter.ANIME
    }

    /**
     * A restore must not be able to narrow a phone or widen a television.
     *
     * `PreferenceBackupCreator` takes everything the store holds, so a backup taken on one carries
     * the other's lens — and a fixed lens that reported itself as *set* would invite a restore to
     * write it. It holds nothing, so there is nothing of it to carry either way.
     */
    @Test
    fun `a fixed lens holds nothing a backup could carry`() {
        val preferences = ContentPreferences(InMemoryPreferenceStore(), fixedTo = ContentFilter.ANIME)

        preferences.contentFilter.isSet() shouldBe false

        preferences.contentFilter.delete()
        preferences.contentFilter.get() shouldBe ContentFilter.ANIME
    }
}
