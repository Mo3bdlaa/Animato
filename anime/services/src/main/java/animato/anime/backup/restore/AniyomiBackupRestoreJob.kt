package animato.anime.backup.restore

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR

/**
 * Runs an Aniyomi import in the background, so closing the app does not lose it.
 *
 * It shares Mihon's restore notification, its cancel action and its unique-work name. Sharing the
 * name is deliberate: an import and a Mihon restore both write to the library, and two of them at
 * once would race. Whichever starts first keeps the slot.
 */
class AniyomiBackupRestoreJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val notifier = BackupNotifier(context)

    override suspend fun doWork(): Result {
        val uri = inputData.getString(LOCATION_URI_KEY)?.toUri()
        val options = inputData.getBooleanArray(OPTIONS_KEY)?.let(RestoreOptions::fromBooleanArray)

        if (uri == null || options == null) return Result.failure()

        setForegroundSafely()

        return try {
            AniyomiBackupRestorer(context, notifier).restore(uri, options)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showRestoreError(context.stringResource(MR.strings.restoring_backup_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showRestoreError(e.message)
                Result.failure()
            }
        } finally {
            context.cancelNotification(Notifications.ID_RESTORE_PROGRESS)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_RESTORE_PROGRESS,
            notifier.showRestoreProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        fun isRunning(context: Context): Boolean = context.workManager.isRunning(TAG)

        fun start(context: Context, uri: Uri, options: RestoreOptions) {
            val request = OneTimeWorkRequestBuilder<AniyomiBackupRestoreJob>()
                .addTag(TAG)
                .setInputData(
                    workDataOf(
                        LOCATION_URI_KEY to uri.toString(),
                        OPTIONS_KEY to options.asBooleanArray(),
                    ),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            context.workManager.cancelUniqueWork(TAG)
        }
    }
}

// Mihon's own restore uses this name. See the class comment for why that is on purpose.
private const val TAG = "BackupRestore"

private const val LOCATION_URI_KEY = "location_uri"
private const val OPTIONS_KEY = "options"
