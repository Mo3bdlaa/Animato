package animato.app.sync

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import animato.anime.backup.create.AnimatoBackupCreator
import animato.anime.backup.restore.AniyomiBackupRestorer
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Keeps two devices' libraries level through a folder they both already see.
 *
 * ## The order, which is the whole correctness argument
 *
 * **Merge first, write second.** A device that writes before it reads publishes a snapshot that
 * does not contain what the other device did, and the other device then merges that stale copy over
 * its own newer state. Reading first means the file this device publishes already contains
 * everything it knows about from both sides, so the folder converges instead of oscillating.
 *
 * ## Why merging is safe to do repeatedly
 *
 * Restore is not replace. Both restorers keep the *greater* of the two values — history takes
 * `max` of the timestamps, a tracker takes `max` of the progress, and an episode that is seen
 * locally stays seen when the backup says otherwise. So merging a slightly older file from another
 * device cannot move anybody backwards, and running twice does nothing the first run did not.
 *
 * The one thing that is genuinely lost is a **deletion**: removing a title on one device and then
 * merging a file that still has it brings it back. That is inherent to merging snapshots without a
 * change log, it is what every backup-based sync in this ecosystem does, and it is stated in the
 * settings text rather than discovered.
 *
 * ## What is deliberately not synced
 *
 * App settings, source settings and extension repositories. They are per-device by nature — a
 * phone and a television do not want the same player defaults — and a sync that quietly replaced
 * them would be the most annoying possible way to find out this feature exists.
 */
class LibrarySyncJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val preferences: SyncPreferences = Injekt.get()

    override suspend fun doWork(): Result {
        if (!preferences.enabled.get()) return Result.success()

        val folderUri = preferences.folderUri.get().takeIf { it.isNotBlank() }
            ?: return Result.success()
        val folder = UniFile.fromUri(context, folderUri.toUri())
            ?: return Result.failure()

        val deviceId = preferences.deviceIdOrCreate()

        return try {
            mergeNewestForeign(folder, deviceId)
            publish(folder, deviceId)
            preferences.lastSyncedAt.set(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            // Retried rather than failed: a shared folder is usually unreachable because the phone
            // is on mobile data or the sync client has not mounted it yet, and both fix themselves.
            logcat(LogPriority.WARN, e) { "Library sync could not complete" }
            Result.retry()
        }
    }

    /**
     * Restores the newest backup written by a *different* device, if it is newer than the last one
     * merged.
     *
     * Only the newest. A device that has been off for a week would otherwise replay every file the
     * others wrote in the meantime, and since each one is a superset of the last that is six
     * restores to reach the state the sixth alone would have produced.
     */
    private suspend fun mergeNewestForeign(folder: UniFile, deviceId: String) {
        val newest = folder.listFiles()
            .orEmpty()
            .mapNotNull { file -> file.name?.let { SyncFile.parse(it) }?.let { it to file } }
            .filter { (parsed, _) -> parsed.deviceId != deviceId }
            .maxByOrNull { (parsed, _) -> parsed.writtenAt }
            ?: return

        val (parsed, file) = newest
        if (parsed.writtenAt <= preferences.lastMergedAt.get()) return

        // The same notifier a manual restore uses. A merge that pulls two hundred titles across
        // is worth a progress notification even when nobody asked for it — a phone that is busy for
        // a minute with no explanation is a phone somebody force-quits.
        AniyomiBackupRestorer(context, BackupNotifier(context)).restore(
            uri = file.uri,
            options = MERGE_OPTIONS,
        )
        preferences.lastMergedAt.set(parsed.writtenAt)
    }

    /**
     * Writes this device's state into the folder, replacing its own previous file.
     *
     * One file per device rather than a history: the folder is a rendezvous and not an archive, and
     * a device joining after a year should read one file rather than choose among three hundred.
     * Whoever wants archives already has automatic backups, which write elsewhere.
     */
    private suspend fun publish(folder: UniFile, deviceId: String) {
        val written = AnimatoBackupCreator(context, isAutoBackup = true)
            .backup(folder.uri, BackupOptions())
        val created = UniFile.fromUri(context, written.toUri()) ?: return

        val name = SyncFile(deviceId, System.currentTimeMillis()).fileName()
        folder.listFiles()
            .orEmpty()
            .filter { file -> file.name?.let { SyncFile.parse(it)?.deviceId } == deviceId }
            .forEach { it.delete() }

        if (!created.renameTo(name)) {
            logcat(LogPriority.WARN) { "Library sync wrote a backup it could not rename to $name" }
        }
    }

    companion object {
        private const val TAG = "AnimatoLibrarySync"

        /**
         * Library, categories and nothing else.
         *
         * The other three options a restore accepts — app settings, source settings, extension
         * repositories — are per-device on purpose. See the class comment.
         */
        private val MERGE_OPTIONS = RestoreOptions(
            libraryEntries = true,
            categories = true,
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
        )

        fun setupTask(context: Context, intervalHours: Int? = null) {
            val preferences = Injekt.get<SyncPreferences>()
            val interval = intervalHours ?: preferences.intervalHours.get()

            if (!preferences.enabled.get() || interval <= 0) {
                context.workManager.cancelUniqueWork(TAG)
                return
            }

            val request = PeriodicWorkRequestBuilder<LibrarySyncJob>(
                interval.toLong(),
                TimeUnit.HOURS,
                // A generous flex so the platform can batch this with whatever else it is waking
                // for. Nothing here is time-sensitive to the minute.
                FLEX_MINUTES,
                TimeUnit.MINUTES,
            )
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        // Unmetered because this uploads a whole library snapshot, and somebody on a
                        // metered connection did not ask for that every twelve hours.
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build(),
                )
                .build()

            context.workManager.enqueueUniquePeriodicWork(
                TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Runs it now, ignoring the network constraint, because somebody pressed a button. */
        fun startNow(context: Context) {
            context.workManager.enqueueUniqueWork(
                "$TAG:now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LibrarySyncJob>().addTag(TAG).build(),
            )
        }

        private const val FLEX_MINUTES = 30L
    }
}

/**
 * A sync file's name, which is the only thing a device reads before deciding to download it.
 *
 * `animato-sync_<device>_<millis>.tachibk`. The timestamp is in the name rather than taken from the
 * file's own modification time because a folder that has been through Dropbox, Syncthing and an SMB
 * share has had its timestamps rewritten by at least one of them.
 */
internal data class SyncFile(val deviceId: String, val writtenAt: Long) {

    fun fileName(): String = "$PREFIX${deviceId}_$writtenAt$SUFFIX"

    companion object {
        private const val PREFIX = "animato-sync_"
        private const val SUFFIX = ".tachibk"

        fun parse(name: String): SyncFile? {
            if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
            val middle = name.removePrefix(PREFIX).removeSuffix(SUFFIX)
            val separator = middle.lastIndexOf('_').takeIf { it > 0 } ?: return null
            val writtenAt = middle.substring(separator + 1).toLongOrNull() ?: return null
            return SyncFile(middle.substring(0, separator), writtenAt)
        }
    }
}
