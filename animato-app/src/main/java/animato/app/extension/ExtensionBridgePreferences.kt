package animato.app.extension

import animato.anime.di.AnimatoScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * How extensions are kept without being managed by hand: opened straight from a store, updated in
 * the background and removed once unused. See [ExtensionMaintenanceJob].
 */
@SingleIn(AnimatoScope::class)
@Inject
class ExtensionBridgePreferences(private val preferenceStore: PreferenceStore) {

    val autoUpdate: Preference<Boolean> = preferenceStore.getBoolean("animato_extensions_auto_update", true)

    /** 0 keeps them. */
    val removeUnusedAfterDays: Preference<Int> =
        preferenceStore.getInt("animato_extensions_remove_unused_days", 30)

    /**
     * Packages installed by opening one of their sources from a store. Only these are ever removed
     * for being unused; an extension somebody installed themselves stays until they uninstall it.
     */
    val onDemandPackages: Preference<Set<String>> =
        preferenceStore.getStringSet("animato_extensions_on_demand", emptySet())

    fun lastUsed(sourceId: Long): Preference<Long> =
        preferenceStore.getLong("animato_source_last_used_$sourceId", 0L)

    fun markUsed(sourceId: Long) {
        lastUsed(sourceId).set(System.currentTimeMillis())
    }
}
