package animato.anime.backup.create

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import animato.anime.backup.restore.AniyomiBackupRestoreJob
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Writes a backup in the background, manually or on a schedule.
 *
 * It takes over Mihon's work names — `BackupCreator` and `BackupCreator:manual` — so that there is
 * one backup job in the app rather than two writing over each other's files. Mihon's job still
 * exists and still works; it simply never holds the slot, because [setupTask] is called after the
 * migrations that would otherwise have claimed it, and again after any restore.
 *
 * Leaving both would not be a tidiness problem. Automatic backups keep the last four, so a
 * manga-only file written by Mihon's job rotates out one of ours, and the user finds out when they
 * need the backup.
 */
class AnimatoBackupCreateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val isAutoBackup = inputData.getBoolean(IS_AUTO_BACKUP_KEY, true)

        // Backing up halfway through a restore would capture a library that is half one thing and
        // half another.
        if (isAutoBackup && (AniyomiBackupRestoreJob.isRunning(context) || BackupRestoreJob.isRunning(context))) {
            return Result.retry()
        }

        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
            ?: Injekt.get<StorageManager>().getAutomaticBackupsDirectory()?.uri
            ?: return Result.failure()

        setForegroundSafely()

        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let(BackupOptions::fromBooleanArray)
            ?: BackupOptions()

        return try {
            val location = AnimatoBackupCreator(context, isAutoBackup).backup(uri, options)
            if (!isAutoBackup) {
                UniFile.fromUri(context, location.toUri())?.let(notifier::showBackupComplete)
            }
            Result.success()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            if (!isAutoBackup) notifier.showBackupError(e.message)
            Result.failure()
        } finally {
            context.cancelNotification(Notifications.ID_BACKUP_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_BACKUP_PROGRESS,
            notifier.showBackupProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isManualJobRunning(context: Context): Boolean = context.workManager.isRunning(TAG_MANUAL)

        /**
         * Puts this job in the periodic slot, at whatever interval the user chose.
         *
         * Call it after anything that might have scheduled Mihon's instead: app start, once the
         * migrations have finished, and the end of a restore.
         */
        fun setupTask(context: Context, prefInterval: Int? = null) {
            val interval = prefInterval ?: Injekt.get<BackupPreferences>().backupInterval.get()
            if (interval <= 0) {
                context.workManager.cancelUniqueWork(TAG_AUTO)
                return
            }

            val request = PeriodicWorkRequestBuilder<AnimatoBackupCreateJob>(
                interval.toLong(),
                TimeUnit.HOURS,
                10,
                TimeUnit.MINUTES,
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .addTag(TAG_AUTO)
                .setConstraints(Constraints(requiresBatteryNotLow = true))
                .setInputData(workDataOf(IS_AUTO_BACKUP_KEY to true))
                .build()

            context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun startNow(context: Context, uri: Uri, options: BackupOptions) {
            val request = OneTimeWorkRequestBuilder<AnimatoBackupCreateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(
                    workDataOf(
                        IS_AUTO_BACKUP_KEY to false,
                        LOCATION_URI_KEY to uri.toString(),
                        OPTIONS_KEY to options.asBooleanArray(),
                    ),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG_MANUAL, ExistingWorkPolicy.KEEP, request)
        }
    }
}

// Mihon's names. See the class comment for why they are shared rather than avoided.
private const val TAG_AUTO = "BackupCreator"
private const val TAG_MANUAL = "$TAG_AUTO:manual"

private const val IS_AUTO_BACKUP_KEY = "is_auto_backup"
private const val LOCATION_URI_KEY = "location_uri"
private const val OPTIONS_KEY = "options"
