package animato.app.crash

import android.app.Application
import android.os.Build
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash, so that finding out about one does not depend on somebody photographing it.
 *
 * ## Why this exists
 *
 * Mihon's handler shows a crash screen with the trace on it and a *Share* button, and records
 * nothing. Press *Restart the application* — which is the button people press — and the trace is
 * gone. Every crash this app has had was reported to us as a **photograph of that screen**, taken
 * because somebody happened to read it before dismissing it, and each of those was luck.
 *
 * ## What it is not
 *
 * It does not send anything anywhere. Automatic reporting needs a server to report *to*, and
 * standing one up is a decision about where a stranger's stack traces are allowed to go — not a
 * decision to make on somebody's behalf inside a bug fix. This writes to the device, and the next
 * launch offers to share it. Nothing leaves the phone unless it is handed somewhere deliberately.
 *
 * ## How it sits under Mihon's
 *
 * Chained, not replaced. `App.onCreate` installs Mihon's handler; this installs itself over the
 * top and calls whatever was there when it is done, so the crash screen still appears exactly as
 * it did. The order matters — recording first means the record survives even if showing the screen
 * is itself what fails.
 */
class CrashRecorder private constructor(
    private val context: Application,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val preferenceStore: PreferenceStore,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        // In its own guard: a process that is already dying must reach the handler below whatever
        // happens here, and a failure to write a file is not worth losing the crash screen over.
        runCatching { record(exception) }
        previous?.uncaughtException(thread, exception)
    }

    private fun record(exception: Throwable) {
        val at = System.currentTimeMillis()
        val report = buildString {
            appendLine("Animato ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine(TIMESTAMP.format(Date(at)))
            appendLine()
            append(exception.stackTraceToString())
        }
        file().writeText(report)
        pendingAt().set(at)
    }

    companion object {
        private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        private const val FILE_NAME = "animato-last-crash.txt"
        private const val PENDING_KEY = "animato_crash_pending_at"

        @Volatile
        private var installed = false

        /**
         * Start recording, once.
         *
         * Guarded rather than idempotent-by-accident: installing twice would chain this handler to
         * itself and write the same crash twice, and the guard is cheaper than making the write
         * tolerate that.
         */
        @Synchronized
        fun install(context: Application) {
            if (installed) return
            installed = true
            Thread.setDefaultUncaughtExceptionHandler(
                CrashRecorder(context, Thread.getDefaultUncaughtExceptionHandler(), Injekt.get()),
            )
        }

        /** When the last unacknowledged crash happened, or null if there is nothing to report. */
        fun pending(): Long? = pendingAt().get().takeIf { it > 0L }

        /** The report itself, or null if the file is gone — which a reinstall or a clear makes it. */
        fun report(context: Application): String? =
            file(context).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

        /**
         * Stop offering it.
         *
         * The file stays. It costs a few kilobytes, and somebody who dismissed the prompt and then
         * changed their mind has nowhere else to get it from.
         */
        fun acknowledge() {
            pendingAt().set(0L)
        }

        private fun pendingAt() = Injekt.get<PreferenceStore>().getLong(PENDING_KEY, 0L)

        private fun file(context: Application = Injekt.get()) = File(context.filesDir, FILE_NAME)
    }
}
