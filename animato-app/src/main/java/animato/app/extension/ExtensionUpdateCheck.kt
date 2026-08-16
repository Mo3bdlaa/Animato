package animato.app.extension

import android.content.Context
import animato.anime.services.AnimeExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier
import kotlinx.coroutines.flow.first
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Tells people when their extensions have updates waiting.
 *
 * ## Why this file exists at all
 *
 * Mihon does this on every cold start, from a `LaunchedEffect` in its `MainActivity`:
 *
 * ```kotlin
 * ExtensionApi().checkForUpdates(context)
 * ```
 *
 * Our `MainActivity` is an adapted copy of theirs, and that block is not in it. `ExtensionApi` is
 * declared `internal`, and `internal` in Kotlin means *this compilation module* — Mihon's `:app` is
 * a different Gradle module from ours, so the class is simply not nameable here. The line did not
 * survive the copy, and nothing said so. So neither half has ever checked: not manga, not anime.
 *
 * That is a quiet failure of exactly the kind this project keeps finding, and the reason it is worth
 * spelling out here rather than fixing silently: a feature can be lost by a visibility modifier, and
 * the only symptom is an absence.
 *
 * ## Why re-implementing it is cheap
 *
 * `ExtensionApi` is a **wrapper over public parts**, not hidden logic. Everything it needs is
 * already public and already used by the browse screens:
 *
 * - `ExtensionManager.findAvailableExtensions()` fetches the repository lists;
 * - it then recomputes `hasUpdate` on every installed extension itself, comparing version code and
 *   library version — so the comparison is not ours to write, only to read;
 * - `ExtensionUpdateNotifier.promptUpdates(names)` is public.
 *
 * So this asks for a refresh, reads the answer the manager worked out, and posts a notification.
 *
 * ## Both halves
 *
 * Mihon's version knows only about manga, because that is all Mihon has. Ours does both, and keeps
 * them apart: two managers, two notifiers, two notification channels. An extension update is
 * per-extension, and someone with a broken anime source does not want to be told about a manga one.
 */
class ExtensionUpdateCheck(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
) {

    /**
     * Refreshes both repository lists and notifies about whatever now has an update.
     *
     * Each half is attempted independently. A source repository being unreachable is ordinary —
     * they go down, and people put dead URLs in — and it must not stop the other half from being
     * checked. Failures are logged rather than surfaced: nobody asked for this check, so nothing
     * about it should interrupt them.
     */
    suspend fun check(context: Context) = withIOContext {
        checkManga(context)
        checkAnime(context)
    }

    private suspend fun checkManga(context: Context) {
        try {
            extensionManager.findAvailableExtensions()
            val names = extensionManager.installedExtensionsFlow.first()
                .filter { it.hasUpdate }
                .map { it.name }

            if (names.isNotEmpty()) {
                ExtensionUpdateNotifier(context).promptUpdates(names)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not check for manga extension updates" }
        }
    }

    private suspend fun checkAnime(context: Context) {
        try {
            animeExtensionManager.findAvailableExtensions()
            val names = animeExtensionManager.installedExtensionsFlow.first()
                .filter { it.hasUpdate }
                .map { it.name }

            if (names.isNotEmpty()) {
                AnimeExtensionUpdateNotifier(context).promptUpdates(names)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Could not check for anime extension updates" }
        }
    }
}
