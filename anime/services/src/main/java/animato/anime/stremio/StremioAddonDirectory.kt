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

    val kind: AddonKind get() = AddonKind.of(resources)

    /**
     * Whether it declares Stremio's `anime` type.
     *
     * The reason the store has an Anime chip. Seventy-four of the addons in the snapshot say this
     * about themselves and the app was reading straight past it, so an anime app's addon store
     * offered five hundred rows with no way to ask for the ones that are the point.
     */
    val servesAnime: Boolean get() = types.any { it.equals(TYPE_ANIME, ignoreCase = true) }
}

private const val TYPE_ANIME = "anime"

/**
 * What an addon actually does for you, as one word.
 *
 * ## The question it answers
 *
 * Stremio addons split the job and meet on a shared id: a catalogue knows what *Spider-Man* is and
 * has no video, a stream provider has video and no idea what it is called. That is the protocol's
 * best idea and its worst first impression — install the wrong one and you get a beautiful grid of
 * posters where nothing plays, with nothing on screen having warned you.
 *
 * A five-hundred-row list sorted by name cannot tell you which you are looking at. This can, and it
 * is read off the manifest rather than guessed: an addon declares its resources, and those are
 * exactly the promise it is making.
 *
 * ## Why it is one kind and not a set
 *
 * An addon serving both catalog and stream genuinely is in two categories, and listing it twice
 * would make the list longer to answer a question about making it shorter. So the kinds are ranked
 * by what is scarcest: something that browses *and* plays is the most useful thing here and gets
 * its own name, and everything else is named for the one job it does.
 */
enum class AddonKind {
    /** Browses and plays. Works on its own, which is what most people are looking for. */
    Complete,

    /** Plays, but has nothing to browse. Adds video behind catalogues already installed. */
    Video,

    /** Browses only. Needs one of the above before anything will play. */
    Catalogue,

    /** Subtitles, and nothing else. Never appears as a source; works behind the ones that do. */
    Subtitles,

    ;

    companion object {
        fun of(resources: List<String>): AddonKind {
            val plays = STREAM in resources
            val browses = CATALOG in resources || META in resources
            return when {
                plays && browses -> Complete
                plays -> Video
                browses -> Catalogue
                else -> Subtitles
            }
        }

        private const val STREAM = "stream"
        private const val CATALOG = "catalog"
        private const val META = "meta"
    }
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
     * Every address the directory calls adult, without going near the network.
     *
     * Only the bundled half is read, and that is not a shortcut: the flag is decided offline by
     * the generator from an addon's own name and description, so [fetched] never carries one and
     * the snapshot is the whole of what is known. What wants this wants it on launch, before
     * anybody has opened a screen — and making that wait on a request to Stremio would mean an
     * addon is briefly not incognito on a bad connection, which is the one moment it matters.
     *
     * Normalised, because it is compared against addresses somebody typed.
     */
    suspend fun adultUrls(): Set<String> = withIOContext {
        bundled().filter { it.isAdult }.map { StremioUrls.normalizeBase(it.url) }.toSet()
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
