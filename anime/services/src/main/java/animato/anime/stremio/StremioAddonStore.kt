package animato.anime.stremio

import android.app.Application
import animato.anime.util.decodeOrSalvage
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
) {
    /**
     * Whether this addon is somewhere you can go, or only something other addons draw on.
     *
     * A stream-only addon like Torrentio has no catalogue at all: as a source it would show empty
     * shelves and an empty search, which reads as broken rather than as "this one supplies video
     * to the others". So it is kept, consulted whenever anything needs a stream, and never listed
     * as a place to browse.
     */
    val isBrowsable: Boolean get() = manifest.serves("catalog")

    /** What a non-browsable addon actually contributes, so its row can say so. */
    val supplies: Supplies
        get() = when {
            manifest.serves("stream") && manifest.serves("subtitles") -> Supplies.STREAMS_AND_SUBTITLES
            manifest.serves("subtitles") -> Supplies.SUBTITLES
            else -> Supplies.STREAMS
        }

    enum class Supplies { STREAMS, SUBTITLES, STREAMS_AND_SUBTITLES }

    /**
     * Every content type this addon says it deals in.
     *
     * Both places it can say so. An addon declares types at the top of its manifest and again on
     * each catalog, and manifests exist that fill in only one of the two — so the answer is the
     * union rather than whichever field happened to be populated.
     */
    private val declaredTypes: Set<String>
        get() = (manifest.types + manifest.catalogs.map { it.type })
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
            .toSet()

    /**
     * Whether this addon carries live channels.
     *
     * True for a dual-purpose addon as well as a pure IPTV one: several publish films *and*
     * channels, and those genuinely belong under both headings rather than under whichever one we
     * happened to pick for them.
     */
    val servesLiveTv: Boolean get() = TYPE_TV in declaredTypes

    /**
     * Whether it carries anything that is not a channel.
     *
     * An addon that declares nothing at all counts as on-demand. That is the older and commoner
     * shape, and a source with no stated type should turn up in the general list rather than
     * vanish from both.
     */
    val servesOnDemand: Boolean
        get() = declaredTypes.isEmpty() || declaredTypes.any { it != TYPE_TV }

    /**
     * Whether it says, in the manifest, that it deals in anime.
     *
     * Stremio has a type for it and 74 of the addons in the bundled directory declare it, which
     * until now was a field this app read past — in an app whose whole subject is anime. It is not
     * a guess about the catalogue's contents: an addon that does not say so is not counted, even
     * where the name makes it obvious, because a name is not a promise and the type is.
     */
    val servesAnime: Boolean get() = TYPE_ANIME in declaredTypes
}

/**
 * Stremio's type for a live channel.
 *
 * A file-level constant, and it has to be. It was a `private companion object` inside
 * [StremioAddon] for two releases, which crashed the app: the serialization plugin puts
 * `serializer()` on a serializable class's companion, so making that companion private makes the
 * generated `Companion` field private too — and [StremioAddonStore], a different class, is what
 * calls it. That compiles and then throws `IllegalAccessError` at runtime under R8.
 *
 * The rule, stated so it is not rediscovered: **a `@Serializable` class must not declare a private
 * companion object.**
 */
private const val TYPE_TV = "tv"

/**
 * Stremio's type for anime, kept beside [TYPE_TV] and file-level for the same reason.
 */
private const val TYPE_ANIME = "anime"

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
        // Streaming and subtitles count. The first version of this check demanded a catalogue or
        // metadata, and so refused Torrentio — an addon that serves nothing but `stream`, and the
        // single most installed addon there is. That was precisely backwards: the single-purpose
        // addons are the ones that turn a listing into something watchable and something you can
        // follow, and refusing them left the app able to install only the half that does neither.
        if (USEFUL_RESOURCES.none { manifest.serves(it) }) {
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

    private fun load(): List<StremioAddon> =
        preferenceStore.decodeOrSalvage(preference, PREF_KEY, emptyList()) {
            json.decodeFromString<List<StremioAddon>>(it)
        }

    private fun failure(resource: dev.icerock.moko.resources.StringResource): Result<StremioAddon> =
        Result.failure(IllegalArgumentException(Injekt.get<Application>().stringResource(resource)))

    companion object {
        private const val PREF_KEY = "animato_stremio_addons"

        /** Anything an addon can offer that this app has a use for. */
        internal val USEFUL_RESOURCES = listOf("catalog", "meta", "stream", "subtitles")
    }
}
