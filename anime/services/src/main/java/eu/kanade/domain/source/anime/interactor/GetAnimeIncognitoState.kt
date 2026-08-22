package eu.kanade.domain.source.anime.interactor

import animato.anime.stremio.StremioAddonStore
import animato.anime.stremio.StremioSource
import aniyomi.domain.source.service.AnimeSourcePreferences
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether what somebody is doing right now should leave a trace.
 *
 * ## Why there are two kinds of key
 *
 * Incognito is stored as a set of strings, and until every source came from an installed APK that
 * string was always a package name. It no longer is: a Stremio addon is an address, and asking the
 * extension manager which package a Stremio source belongs to answers null — so an addon could not
 * be marked incognito at all, and the app's own rule that adult sources are private by default
 * quietly did not apply to the one kind of source where adult content is most of what is on offer.
 *
 * So a source with no package falls back to its address, which is what a Stremio addon *is*. One
 * set holding two shapes of key is a small untidiness bought deliberately: the alternative is a
 * second preference and a second lookup in every caller, to express the same fact.
 */
class GetAnimeIncognitoState(
    private val basePreferences: BasePreferences,
    private val sourcePreferences: AnimeSourcePreferences,
    private val extensionManager: AnimeExtensionManager,
    private val stremioStore: StremioAddonStore,
) {
    fun await(sourceId: Long?): Boolean {
        if (basePreferences.incognitoMode.get()) return true
        if (sourceId == null) return false
        val key = keyFor(sourceId) ?: return false
        return key in sourcePreferences.incognitoAnimeExtensions.get()
    }

    fun subscribe(sourceId: Long?): Flow<Boolean> {
        if (sourceId == null) return basePreferences.incognitoMode.changes()
        return combine(
            basePreferences.incognitoMode.changes(),
            sourcePreferences.incognitoAnimeExtensions.changes(),
            extensionManager.getExtensionPackageAsFlow(sourceId),
        ) { incognito, incognitoExtensions, extensionPackage ->
            val key = extensionPackage ?: stremioUrlFor(sourceId)
            incognito || (key != null && key in incognitoExtensions)
        }
            .distinctUntilChanged()
    }

    private fun keyFor(sourceId: Long): String? =
        extensionManager.getExtensionPackage(sourceId) ?: stremioUrlFor(sourceId)

    /**
     * The address behind a Stremio source id, if this id is one.
     *
     * Walked rather than looked up, because the id is a hash of the address and hashes only go one
     * way. The list is the handful of addons somebody has added, so walking it is cheaper than the
     * reverse index it would take to avoid walking it.
     */
    private fun stremioUrlFor(sourceId: Long): String? =
        stremioStore.addons.value.firstOrNull { StremioSource.idFor(it.url) == sourceId }?.url
}
