package animato.anime.stremio

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * One Stremio addon as the app remembers it: where it lives, and what it last said it was.
 */
@Serializable
data class StremioAddon(
    val url: String,
    val manifest: StremioManifest,
)

/**
 * The addons the user has added, and the only place they are added or removed.
 *
 * The manifest is stored alongside the URL rather than fetched at startup. A source has to exist
 * before anything can be listed under it — its name, its catalogs and its id all come out of the
 * manifest — so fetching on launch would mean an app that opens with no sources and grows them a
 * second later, and no sources at all when the network is down. Instead the last known manifest
 * is what the app starts from, and [refresh] catches up in the background.
 *
 * Preferences rather than a table: this is a handful of URLs, it has no relations, and nothing
 * else in the app reads it. A schema migration would cost more than the data is worth.
 */
class StremioAddonStore(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    private val preference = preferenceStore.getString(PREF_KEY, "")

    private val _addons = MutableStateFlow(load())
    val addons: StateFlow<List<StremioAddon>> = _addons.asStateFlow()

    /**
     * Fetch the manifest at [rawUrl] and, if it really is an addon, keep it.
     *
     * Every failure here is a sentence rather than an exception class, because every one of them
     * is something the person who just typed a URL can act on: a typo, a site that is down, a
     * page that is not an addon at all, or — the one that trips everybody — a configurable addon
     * whose bare URL serves nothing until you have configured it on its own page.
     */
    suspend fun install(rawUrl: String): Result<StremioAddon> {
        val base = StremioUrls.normalizeBase(rawUrl)
        if (base.isEmpty() || !base.startsWith("http")) {
            return failure(AYMR.strings.stremio_error_invalid_url)
        }
        if (_addons.value.any { it.url.equals(base, ignoreCase = true) }) {
            return failure(AYMR.strings.stremio_error_already_added)
        }

        val manifest = try {
            val response = networkHelper.client.newCall(GET(StremioUrls.manifest(base))).awaitSuccess()
            with(json) { response.parseAs<StremioManifest>() }
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Stremio addon unreachable: $base" }
            return failure(AYMR.strings.stremio_error_unreachable)
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Stremio manifest unreadable: $base" }
            return failure(AYMR.strings.stremio_error_not_an_addon)
        }

        if (manifest.name.isBlank() && manifest.id.isBlank()) {
            return failure(AYMR.strings.stremio_error_not_an_addon)
        }
        if (manifest.behaviorHints.configurationRequired) {
            return failure(AYMR.strings.stremio_error_needs_configuration)
        }
        if (!manifest.serves(RESOURCE_CATALOG) && !manifest.serves(RESOURCE_META)) {
            return failure(AYMR.strings.stremio_error_no_content)
        }

        val addon = StremioAddon(url = base, manifest = manifest)
        persist(_addons.value + addon)
        return Result.success(addon)
    }

    fun remove(url: String) {
        persist(_addons.value.filterNot { it.url.equals(url, ignoreCase = true) })
    }

    /**
     * Re-read every manifest, keeping the stored one wherever the addon does not answer.
     *
     * An addon that is briefly down must not lose its catalogs — dropping to an empty manifest
     * would empty the source and, worse, silently change nothing visible until someone opened it.
     */
    suspend fun refresh() {
        val refreshed = _addons.value.map { addon ->
            runCatching {
                val response = networkHelper.client.newCall(GET(StremioUrls.manifest(addon.url))).awaitSuccess()
                addon.copy(manifest = with(json) { response.parseAs<StremioManifest>() })
            }.getOrElse {
                logcat(LogPriority.INFO, it) { "Keeping stored manifest for ${addon.url}" }
                addon
            }
        }
        persist(refreshed)
    }

    private fun persist(addons: List<StremioAddon>) {
        preference.set(json.encodeToString(addons))
        _addons.value = addons
    }

    private fun load(): List<StremioAddon> {
        val raw = preference.get()
        if (raw.isEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<StremioAddon>>(raw) }
            .getOrElse {
                logcat(LogPriority.ERROR, it) { "Stored Stremio addons could not be read" }
                emptyList()
            }
    }

    private fun failure(resource: dev.icerock.moko.resources.StringResource): Result<StremioAddon> =
        Result.failure(IllegalArgumentException(Injekt.get<Application>().stringResource(resource)))

    companion object {
        private const val PREF_KEY = "animato_stremio_addons"
        private const val RESOURCE_CATALOG = "catalog"
        private const val RESOURCE_META = "meta"
    }
}
