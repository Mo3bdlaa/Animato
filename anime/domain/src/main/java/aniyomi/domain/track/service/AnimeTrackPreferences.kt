package aniyomi.domain.track.service

import tachiyomi.core.common.preference.PreferenceStore

/**
 * Tracking preferences that Mihon's [eu.kanade.domain.track.service.TrackPreferences] does not have.
 *
 * Aniyomi added the first two to Mihon's own class. One is plainly anime-only — Mihon has no airing
 * times to show. The other is not, but Mihon still does not offer it, and adding it to Mihon's file
 * is the move this project exists to avoid. Their keys are Aniyomi's, so an imported install keeps
 * both settings.
 */
class AnimeTrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /** Whether adding an entry to the library also starts tracking it. */
    fun trackOnAddingToLibrary() = preferenceStore.getBoolean("track_on_adding_to_library", true)

    /** Whether an anime's page shows a countdown to the next episode's air time. */
    fun showNextEpisodeAiringTime() = preferenceStore.getBoolean("show_next_episode_airing_time", true)

    /**
     * Whether a tracker's own progress is written back into the library.
     *
     * Tracking is otherwise one-way: the app tells the tracker what was watched and never listens.
     * Watch three episodes somewhere else and the app still shows none of them seen.
     *
     * Off by default, because this is not a display setting — turning it on marks episodes watched,
     * and the first sync of a long-tracked anime can mark a great many at once. Whoever wants that
     * should be the one to say so.
     */
    fun syncProgressFromTracker() = preferenceStore.getBoolean("sync_progress_from_tracker", false)
}
