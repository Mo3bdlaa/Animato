package animato.app.crash

import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Notices when the app cannot start, so that a fixed release can be reached from a broken one.
 *
 * ## The situation this is for
 *
 * A build shipped that asked the dependency graph for something nobody had registered, in work
 * launched from `onCreate`. An exception in a coroutine started there is an exception in `onCreate`,
 * so the app died on the splash screen — every launch, identically, with no screen to press
 * anything on. The fix took ten minutes to write and there was no way to deliver it: the update
 * check lives inside the app, and the app never got far enough to run it.
 *
 * That is the gap. A crash on the way in is the one kind of crash that also disables the mechanism
 * for fixing crashes, and the only way out was to notice the failure *before* attempting the thing
 * that fails.
 *
 * ## How a failed start is recognised
 *
 * A counter, incremented at the top of every launch and cleared once the app has been up and
 * interactive for a few seconds. Not cleared at the end of `onCreate`, and not on the first frame:
 * the crash that prompted this arrived from a coroutine a moment *after* `onCreate` returned, so
 * either of those would have called it a success. Surviving a few seconds is a weaker claim than
 * "started correctly" and a much stronger one than "reached the end of a method".
 *
 * ## Why two, and not one
 *
 * One crash is an accident — a bad network moment, a corrupt file, something that will not repeat.
 * Diverting somebody to a recovery screen over it would make the app feel broken when it is not.
 * Two consecutive failures without a single successful start in between is a build that does not
 * work on this device, which is the only case worth interrupting for.
 */
object StartupGuard {

    /**
     * Count this attempt, and say whether the previous ones already failed.
     *
     * Returns the number of consecutive launches that have not reached [succeeded], including this
     * one — so `1` is an ordinary launch and anything from [LOOP_THRESHOLD] up is a build that has
     * not managed to start.
     */
    fun beginStartup(): Int {
        val attempts = attempts()
        val next = attempts.get() + 1
        attempts.set(next)
        return next
    }

    /** Whether this launch should go to recovery rather than into an app that will not open. */
    fun isLooping(): Boolean = attempts().get() >= LOOP_THRESHOLD

    /**
     * The app is up. Called from a delay rather than a lifecycle callback — see the class note.
     *
     * Idempotent and cheap, so nothing needs to work out whether it has already run.
     */
    fun succeeded() {
        attempts().set(0)
    }

    /**
     * Forget the failures without having started successfully.
     *
     * For *open anyway*: somebody who chooses to go in past the warning is not asking to be sent
     * back to it on their next launch, and if the app really is broken the counter fills up again
     * in two more attempts.
     */
    fun forget() {
        attempts().set(0)
    }

    private fun attempts() = Injekt.get<PreferenceStore>().getInt(ATTEMPTS_KEY, 0)

    /** How long the app must stay up before the launch counts as having worked. */
    const val SETTLE_MILLIS = 5_000L

    private const val LOOP_THRESHOLD = 2
    private const val ATTEMPTS_KEY = "animato_startup_attempts"
}
