package animato.app.crash

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Why Android ended the last session, when it was Android that ended it.
 *
 * ## The gap this closes
 *
 * [CrashRecorder] sees an exception, because an exception passes through the app on its way out.
 * Two of the ways a player dies never do: the system killing a process that stopped responding —
 * an ANR — and the system killing one to get memory back. Both were reported from a television the
 * same way, *it was about to start the video and then it was gone, or it froze*, and the only
 * evidence of either was on the device, in a place nobody without a cable can read.
 *
 * Android 11 started keeping it: [ActivityManager.getHistoricalProcessExitReasons] says why each
 * recent process of this app ended, and for an ANR it hands back the very thread dump the system
 * took — which thread was stuck, and on what. This reads that on launch and, when the last session
 * ended in one of those ways while it was on screen, keeps it as the report the crash prompt offers
 * to share. Same prompt, same file, same *nothing leaves the device unless somebody sends it*.
 *
 * Below Android 11 there is nothing to read, and this does nothing.
 */
internal object ExitReasons {

    private const val SEEN_KEY = "animato_exit_reason_seen_at"

    /** Kept short enough to go through a share sheet; the stuck thread is near the top anyway. */
    private const val TRACE_LIMIT = 96 * 1024

    fun check(context: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching { checkR(context) }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkR(context: Application) {
        val seen = Injekt.get<PreferenceStore>().getLong(SEEN_KEY, 0L).get()
        val exits = context.getSystemService<ActivityManager>()
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            .orEmpty()
            .filter { it.timestamp > seen }
        if (exits.isEmpty()) return
        Injekt.get<PreferenceStore>().getLong(SEEN_KEY, 0L).set(exits.maxOf { it.timestamp })

        // The newest that is worth telling somebody about. A cached process reclaimed for memory
        // while nothing of it was showing is Android working as intended, not a failure.
        val exit = exits.sortedByDescending { it.timestamp }.firstOrNull(::worthReporting) ?: return
        CrashRecorder.save(describe(exit), exit.timestamp, context)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun worthReporting(exit: ApplicationExitInfo): Boolean {
        val wasVisible = exit.importance <= RunningAppProcessInfo.IMPORTANCE_VISIBLE
        return when (exit.reason) {
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
            -> true
            // How a memory kill is recorded depends on the device: as low memory where the system
            // can tell, and as a plain SIGKILL where the kernel's killer acted on its own.
            ApplicationExitInfo.REASON_LOW_MEMORY,
            ApplicationExitInfo.REASON_SIGNALED,
            -> wasVisible
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(exit: ApplicationExitInfo): String = buildString {
        // The first line is the one the prompt shows, so it is the plain-words version.
        appendLine("Closed by Android: ${reasonName(exit.reason)}")
        appendLine()
        appendLine("Process: ${exit.processName}")
        appendLine("Importance: ${exit.importance}")
        appendLine("Status: ${exit.status}")
        exit.description?.let { appendLine("Description: $it") }
        appendLine("Memory: pss ${exit.pss / 1024} MB, rss ${exit.rss / 1024} MB")
        deviceMemory()?.let { appendLine("Device memory: $it") }

        if (exit.reason == ApplicationExitInfo.REASON_ANR) {
            val trace = runCatching {
                exit.traceInputStream?.use { stream ->
                    val bytes = stream.readNBytesCompat(TRACE_LIMIT)
                    String(bytes)
                }
            }.getOrNull()
            if (!trace.isNullOrBlank()) {
                appendLine()
                append(trace)
            }
        }
    }

    private fun deviceMemory(): String? = runCatching {
        val manager = Injekt.get<Application>().getSystemService<ActivityManager>() ?: return@runCatching null
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        "total ${info.totalMem / MIB} MB, low-RAM device ${manager.isLowRamDevice}, " +
            "per-app limit ${manager.memoryClass} MB"
    }.getOrNull()

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "out of memory"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by the system (likely out of memory)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource use"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        else -> "reason $reason"
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buffer.size() < limit) {
            val read = read(chunk, 0, minOf(chunk.size, limit - buffer.size()))
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private const val MIB = 1024 * 1024
}
