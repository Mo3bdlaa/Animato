package animato.anime.stremio

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
) {
    private var cached: List<DirectoryAddon>? = null

    suspend fun listed(): List<DirectoryAddon> {
        cached?.let { return it }
        return runCatching {
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
                .filter { it.isUseful }
                // Same address twice is the same addon; the collection has a few, published under
                // two names as their authors renamed them.
                .distinctBy { StremioUrls.normalizeBase(it.url) }
                .sortedBy { it.name.lowercase() }
                .also { cached = it }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val COLLECTION_URL = "https://api.strem.io/addonscollection.json"
    }
}
