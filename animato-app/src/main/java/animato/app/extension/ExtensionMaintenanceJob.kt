package animato.app.extension

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import animato.anime.services.AnimeInjektHook
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.BasePreferences.ExtensionInstaller
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.manga.interactor.GetFavorites
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Keeps extensions current and the device free of ones nobody uses, so nobody has to install,
 * update and uninstall them one at a time — which on a television remote is most of the work.
 *
 * Updates only with the private installer: any other one puts a system prompt in front of somebody
 * who is not looking. Removes only what was installed by opening a source from a store, unused for
 * the chosen number of days, and with nothing in the library.
 */
class ExtensionMaintenanceJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    init {
        AnimeInjektHook.ensure()
    }

    private val preferences: ExtensionBridgePreferences = Injekt.get()
    private val extensionManager: ExtensionManager = Injekt.get()
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get()
    private val getFavorites: GetFavorites = Injekt.get()
    private val getAnimeFavorites: GetAnimeFavorites = Injekt.get()

    override suspend fun doWork(): Result {
        try {
            updateAll()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not update extensions" }
        }
        try {
            removeUnused()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Could not remove unused extensions" }
        }
        return Result.success()
    }

    private suspend fun updateAll() {
        if (!updatesSilently()) return

        runCatching { extensionManager.findAvailableExtensions() }
        extensionManager.getLoadedExtensions()
            .filter { it.hasUpdate }
            .forEach { extensionManager.updateExtension(it).awaitEnd() }

        runCatching { animeExtensionManager.findAvailableExtensions() }
        animeExtensionManager.installedExtensionsFlow.first()
            .filter { it.hasUpdate }
            .forEach { animeExtensionManager.updateExtension(it).awaitEnd() }
    }

    private suspend fun removeUnused() {
        val days = preferences.removeUnusedAfterDays.get()
        val onDemand = preferences.onDemandPackages.get()
        if (days <= 0 || onDemand.isEmpty()) return

        val cutoff = System.currentTimeMillis() - days.days.inWholeMilliseconds
        val inLibrary = (getFavorites.await().map { it.source } + getAnimeFavorites.await().map { it.source }).toSet()
        fun isUnused(sourceIds: List<Long>) =
            isUnused(sourceIds, inLibrary, cutoff) { preferences.lastUsed(it).get() }

        val manga = extensionManager.getLoadedExtensions().filter { it.pkgName in onDemand && !it.isShared }
        val anime = animeExtensionManager.installedExtensionsFlow.first()
            .filter { it.pkgName in onDemand && !it.isShared }

        val removed = buildSet {
            manga.filter { isUnused(it.sources.map { source -> source.id }) }.forEach {
                extensionManager.uninstallExtension(it)
                add(it.pkgName)
            }
            anime.filter { isUnused(it.sources.map { source -> source.id }) }.forEach {
                animeExtensionManager.uninstallExtension(it)
                add(it.pkgName)
            }
        }
        // Forget packages uninstalled since, by hand or here
        val stillInstalled = (manga.map { it.pkgName } + anime.map(AnimeExtension.Installed::pkgName)).toSet()
        preferences.onDemandPackages.set(onDemand.intersect(stillInstalled) - removed)
    }

    private suspend fun Flow<InstallStep>.awaitEnd() {
        withTimeoutOrNull(INSTALL_TIMEOUT) { first { it.isCompleted() } }
    }

    companion object {
        private const val TAG = "AnimatoExtensionMaintenance"
        private const val TAG_NOW = "AnimatoExtensionMaintenanceNow"
        private val INSTALL_TIMEOUT = 5.minutes

        fun setupTask(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExtensionMaintenanceJob>(12, TimeUnit.HOURS, 2, TimeUnit.HOURS)
                .addTag(TAG)
                .setConstraints(connected())
                .build()
            context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<ExtensionMaintenanceJob>()
                .addTag(TAG_NOW)
                .setConstraints(connected())
                .build()
            context.workManager.enqueueUniqueWork(TAG_NOW, ExistingWorkPolicy.KEEP, request)
        }

        /** Whether updates are applied rather than announced. */
        fun updatesSilently(): Boolean {
            val preferences = Injekt.get<ExtensionBridgePreferences>()
            return preferences.autoUpdate.get() &&
                Injekt.get<BasePreferences>().extensionInstaller.get() == ExtensionInstaller.PRIVATE
        }

        private fun connected() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}

/**
 * Whether an extension serving [sourceIds] can go: none of them has anything in the library, and
 * none was opened since [cutoff]. One that has never been opened counts as just used, so an
 * extension is never removed on the strength of a record that was never written.
 */
internal fun isUnused(
    sourceIds: List<Long>,
    inLibrary: Set<Long>,
    cutoff: Long,
    lastUsed: (Long) -> Long,
): Boolean {
    if (sourceIds.isEmpty() || sourceIds.any { it in inLibrary }) return false
    val usedAt = sourceIds.maxOf(lastUsed)
    return usedAt in 1..<cutoff
}
