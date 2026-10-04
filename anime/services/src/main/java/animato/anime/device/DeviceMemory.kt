package animato.anime.device

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * How much memory this device has to spend on playing video, in three sizes.
 *
 * One answer for every buffer in the playback path. There are two of them — the torrent server's
 * piece cache and mpv's demuxer cache — and they used to be sized separately: mpv by the device's
 * memory, the torrent server at a flat 192 MB whatever it ran on. On a phone with 8 GB that is
 * invisible. On a television with 2 GB, playing a torrent held 192 MB of pieces plus mpv's own copy
 * of the same video plus the app, and Android answered by killing everything else on the set —
 * the launcher, the system UI — which from the sofa is the whole television freezing.
 *
 * Read off the device's total memory, not its Android version or its form factor: a 4 GB television
 * is a perfectly good player, and a 2 GB phone is not.
 */
enum class DeviceMemory {
    /** The device says it is short of memory, or has under 2 GiB. Most Android televisions. */
    Low,

    /** Under 4 GiB. Enough to play video and not much else alongside it. */
    Modest,

    /** Anything current. */
    Roomy,
    ;

    companion object {
        /**
         * Asked once per playback, which is cheap. A missing service answers [Low], the right way
         * round to be wrong: a buffer too small is a stall, a buffer too large is the system
         * killing things.
         */
        fun of(context: Context): DeviceMemory {
            val manager = context.getSystemService<ActivityManager>() ?: return Low
            if (manager.isLowRamDevice) return Low
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            val gib = memory.totalMem / BYTES_PER_GIB
            return when {
                gib < LOW_GIB -> Low
                gib < MODEST_GIB -> Modest
                else -> Roomy
            }
        }

        /*
         * Whole gibibytes, rounded down, because a device sold as "2 GB" reports a little under
         * that once the kernel and the GPU have taken theirs — so a 2 GB television lands in [Low],
         * which is where it belongs.
         */
        private const val LOW_GIB = 2
        private const val MODEST_GIB = 4
        private const val BYTES_PER_GIB = 1024L * 1024 * 1024
    }
}
