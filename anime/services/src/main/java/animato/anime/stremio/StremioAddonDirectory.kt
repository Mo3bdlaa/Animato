package animato.anime.stremio

import android.app.Application
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One addon somebody else published, as it appears in the list.
 *
 * Flattened from the wire shape on purpose: the manifest is only needed to answer *what is this
 * and is it any use to us*, and the screen that shows this has no business holding a protocol
 * model it would then have to interpret.
 */
data class DirectoryAddon(
    val name: String,
    val description: String,
    val url: String,
    val resources: List<String>,
    val types: List<String>,
    /**
     * Whether the addon says, in its own words, that it serves adult content.
     *
     * Read off the bundled snapshot, where it was decided by matching the name and description —
     * the manifest format has no flag for it, so there is nothing better available. Marked rather
     * than dropped: this app hides NSFW sources by default and shows them when the setting is
     * turned on, and a directory that removed them outright would be overriding that setting in
     * the one place a person could not undo it.
     */
    val isAdult: Boolean = false,
) {
    /** Whether it is worth listing at all — see [StremioAddonStore.USEFUL_RESOURCES]. */
    val isUseful: Boolean get() = resources.any { it in StremioAddonStore.USEFUL_RESOURCES }
}

@Serializable
private data class CollectionEntry(
    val transportUrl: String = "",
    val manifest: StremioManifest = StremioManifest(),
)

/**
 * Everybody's addons, from the list Stremio itself publishes.
 *
 * ## Why this exists rather than a longer hardcoded list
 *
 * [SUGGESTED_ADDONS] is four entries and was always meant to be: enough to get a working setup,
 * chosen by us. The request that followed it — *more sources* — cannot be answered the same way.
 * Picking twenty by hand would be a list that is wrong within a month, and every addon on it would
 * be there because we said so, which is a thing to be careful about when the addons are strangers'
 * websites.
 *
 * Stremio publishes its community collection as plain JSON at a stable address, with no key and no
 * account: about ninety addons, each with the manifest already fetched. So the list is *theirs*,
 * kept up to date by them, and this reads it. The distinction matters: we are pointing at a
 * directory, not curating one.
 *
 * ## And why there is a second one bundled with the app
 *
 * Stremio's own collection turned out to be the smaller half. stremio-addons.net is where the
 * community actually publishes — five hundred addons against ninety — and it is a website with no
 * API: the list is rendered into its pages and nothing serves it as data. Scraping that at runtime
 * would put a parser for somebody else's HTML in the request path of a screen, and it would break
 * on their next deploy without anybody noticing.
 *
 * So it is scraped once, offline, by `docs/stremio/build-addon-directory.py`, and the result ships
 * as an asset. The trade is stated plainly: the bundled half is a snapshot and goes stale between
 * releases, while the fetched half is always current. Merging them means the addons that matter
 * most are in both — and where an entry appears twice, the fetched one wins, because it was
 * checked more recently than the file.
 *
 * ## What is filtered out and why
 *
 * The collection includes addons that do nothing this app can use — a stream provider whose streams
 * are all `externalUrl`, a thing that puts a clock in the subtitle track — but there is no way to
 * tell that from a manifest. What *is* visible is the resources it declares, so anything that
 * serves none of the four we speak is dropped, and the rest are listed as they come.
 *
 * Failure is an empty list. This is a nice-to-have section on a screen that works without it, and
 * an error banner about a directory nobody asked to load would be noise.
 */
class StremioAddonDirectory(
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val context: Application = Injekt.get(),
) {
    private var cached: List<DirectoryAddon>? = null

    suspend fun listed(): List<DirectoryAddon> = withIOContext {
        cached?.let { return@withIOContext it }
        // Fetched first, so that where the same addon is in both the current answer is the one
        // kept — distinctBy keeps the first of each address it sees.
        val merged = (fetched() + bundled())
            .filter { it.isUseful }
            // Same address twice is the same addon: within Stremio's collection a few are
            // published under two names as their authors renamed them, and across the two lists
            // every popular addon is in both.
            .distinctBy { StremioUrls.normalizeBase(it.url) }
            .sortedBy { it.name.lowercase() }
        merged.also { cached = it }
    }

    /**
     * Stremio's own collection, or nothing.
     *
     * Failure is an empty list rather than an error. There is a bundled list behind this, so a
     * failed fetch costs freshness rather than the whole section, and a banner about a directory
     * nobody asked to load would be noise.
     */
    private suspend fun fetched(): List<DirectoryAddon> = runCatching {
        val response = network.client.newCall(GET(COLLECTION_URL)).awaitSuccess()
        with(json) { response.parseAs<List<CollectionEntry>>() }
            .filter { it.transportUrl.isNotBlank() }
            .map { entry ->
                DirectoryAddon(
                    name = entry.manifest.name.takeIf { it.isNotBlank() } ?: entry.transportUrl,
                    description = entry.manifest.description,
                    url = entry.transportUrl,
                    resources = entry.manifest.resourceNames.map { it.lowercase() },
                    types = entry.manifest.types,
                )
            }
    }.getOrDefault(emptyList())

    /**
     * The snapshot that ships with the app — see the class note for where it comes from.
     *
     * Read once and held, because it is a few hundred kilobytes of JSON and the screen that wants
     * it is opened repeatedly. A missing or unreadable asset is an empty list: it would mean a
     * broken build rather than a broken device, and there is nothing a person could do about it.
     */
    private fun bundled(): List<DirectoryAddon> = runCatching {
        val text = context.assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString<List<BundledEntry>>(text).map {
            DirectoryAddon(
                name = it.name,
                description = it.description,
                url = it.url,
                resources = it.resources.map(String::lowercase),
                types = it.types,
                isAdult = it.adult,
            )
        }
    }.getOrElse {
        logcat(LogPriority.WARN, it) { "Bundled Stremio addon directory could not be read" }
        emptyList()
    }

    private companion object {
        const val COLLECTION_URL = "https://api.strem.io/addonscollection.json"
        const val BUNDLED_ASSET = "stremio-addons.json"
    }
}

/** One row of the bundled snapshot, which is already flat — see the generator script. */
@Serializable
private data class BundledEntry(
    val name: String = "",
    val description: String = "",
    val url: String = "",
    val resources: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val adult: Boolean = false,
)
