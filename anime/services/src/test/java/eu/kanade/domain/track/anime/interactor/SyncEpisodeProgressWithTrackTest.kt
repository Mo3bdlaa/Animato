package eu.kanade.domain.track.anime.interactor

import aniyomi.domain.track.service.AnimeTrackPreferences
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore

/**
 * Which trackers are allowed to write progress back into the library.
 *
 * The rule, not the writing: whether an episode ends up marked watched is the database's business,
 * and this is the decision that comes before it — the one that was silently "no" for six of the
 * seven trackers.
 */
@Execution(ExecutionMode.CONCURRENT)
class SyncEpisodeProgressWithTrackTest {

    @Test
    fun `an ordinary tracker is not pulled from unless asked`() {
        syncer(enabled = false).shouldSync(ordinaryTracker) shouldBe false
        syncer(enabled = true).shouldSync(ordinaryTracker) shouldBe true
    }

    @Test
    fun `an enhanced tracker is always pulled from`() {
        // Jellyfin is the server the episodes were played from, so its progress is not a second
        // opinion to reconcile. Turning the setting off must not turn that off.
        syncer(enabled = false).shouldSync(enhancedTracker) shouldBe true
        syncer(enabled = true).shouldSync(enhancedTracker) shouldBe true
    }

    private val ordinaryTracker = mockk<AnimeTracker>(relaxed = true)

    private val enhancedTracker = mockk<EnhancedTestTracker>(relaxed = true)

    private fun syncer(enabled: Boolean): SyncEpisodeProgressWithTrack {
        // Seeded rather than set: InMemoryPreferenceStore hands out a fresh Preference on every
        // call and only remembers what it was constructed with, so a `set` here would be written to
        // an object nothing else ever sees.
        val store = InMemoryPreferenceStore(
            sequenceOf(InMemoryPreferenceStore.InMemoryPreference(PREFERENCE_KEY, enabled, false)),
        )

        return SyncEpisodeProgressWithTrack(
            updateEpisode = mockk(relaxed = true),
            insertTrack = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            trackPreferences = AnimeTrackPreferences(store),
        )
    }

    /** Only so a mock can be both at once, which is what Jellyfin is. */
    private interface EnhancedTestTracker : AnimeTracker, EnhancedAnimeTracker

    private companion object {
        /**
         * The stored key, spelled out rather than read from the class under test — which is what
         * makes the enabled case fail if the two ever stop matching.
         */
        const val PREFERENCE_KEY = "sync_progress_from_tracker"
    }
}
