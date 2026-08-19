package animato.anime.stremio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The wire format of a Stremio addon.
 *
 * A Stremio addon is not a program — it is a website that answers four questions in JSON, over
 * plain HTTP, with CORS headers. There is no APK to install, no class loader, no signature to
 * pin: adding one is adding a URL. That is the whole reason it is worth having. Every other
 * source in this app is code we downloaded and ran inside our own process; these are strangers
 * we only ever talk to.
 *
 * The models here are deliberately forgiving. Addons in the wild are written by hobbyists against
 * an SDK that has drifted for years, so nearly every field is optional and a handful arrive with
 * two different types depending on who wrote the addon — [primitiveText] exists for exactly that.
 * A missing field must cost a detail, never the whole response.
 */
@Serializable
data class StremioManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String = "",
    val resources: List<JsonElement> = emptyList(),
    val types: List<String> = emptyList(),
    val catalogs: List<StremioCatalog> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val logo: String? = null,
    val background: String? = null,
    val behaviorHints: StremioManifestHints = StremioManifestHints(),
) {
    /**
     * The resource names the addon serves.
     *
     * `resources` is the one place the spec allows two shapes: a bare string (`"catalog"`) or an
     * object that also narrows the types and id prefixes it answers for. We only need the name,
     * so both shapes collapse to one list rather than leaking the distinction outwards.
     */
    val resourceNames: List<String>
        get() = resources.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNullSafe()
                is JsonObject -> (element["name"] as? JsonPrimitive)?.contentOrNullSafe()
                else -> null
            }
        }

    fun serves(resource: String): Boolean = resourceNames.any { it.equals(resource, ignoreCase = true) }

    /**
     * Whether this addon will answer for one particular thing, rather than merely in general.
     *
     * Addons narrow themselves twice. The manifest-level `types` and `idPrefixes` say what the
     * whole addon is about, and the object form of a resource entry can narrow further — a stream
     * provider that handles films and series but only for IMDb ids says so there. Asking an addon
     * for something it has already declared it does not do wastes a request and, worse, returns an
     * empty answer that is indistinguishable from "nothing is available".
     *
     * An absent list is no constraint at all, not an empty one.
     */
    fun canServe(resource: String, type: String, id: String): Boolean {
        val entries = resources.filter { element ->
            val name = when (element) {
                is JsonPrimitive -> element.contentOrNullSafe()
                is JsonObject -> (element["name"] as? JsonPrimitive)?.contentOrNullSafe()
                else -> null
            }
            name.equals(resource, ignoreCase = true)
        }
        if (entries.isEmpty()) return false

        return entries.any { element ->
            val scopedTypes = (element as? JsonObject)?.get("types").stringList() ?: types
            val scopedPrefixes = (element as? JsonObject)?.get("idPrefixes").stringList() ?: idPrefixes
            (scopedTypes.isEmpty() || scopedTypes.any { it.equals(type, ignoreCase = true) }) &&
                (scopedPrefixes.isEmpty() || scopedPrefixes.any { id.startsWith(it) })
        }
    }
}

private fun JsonElement?.stringList(): List<String>? =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }

@Serializable
data class StremioManifestHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
)

@Serializable
data class StremioCatalog(
    val type: String = "",
    val id: String = "",
    val name: String? = null,
    val extra: List<StremioExtra> = emptyList(),
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList(),
) {
    /**
     * What to call this catalog on screen.
     *
     * The type is part of the name because catalog names are only unique within a type, and
     * routinely are not across them. Cinemeta publishes eight catalogs called Popular, New,
     * Featured — twice each, once for films and once for series. Without the type the picker
     * reads as a list of duplicates, and anything that looks a catalog up by name finds whichever
     * came first.
     */
    val displayName: String
        get() {
            val base = name?.takeIf { it.isNotBlank() } ?: id.takeIf { it.isNotBlank() } ?: type
            return if (type.isBlank() || base == type) base else "$base ($type)"
        }

    /**
     * Whether an extra argument may be passed to this catalog.
     *
     * Two generations of the SDK are in circulation: the current one describes extras as objects
     * under `extra`, the older one as bare names under `extraSupported`. Addons that predate the
     * change still work in Stremio itself, so they have to work here.
     */
    fun supports(extraName: String): Boolean =
        extra.any { it.name.equals(extraName, ignoreCase = true) } ||
            extraSupported.any { it.equals(extraName, ignoreCase = true) }

    fun requires(extraName: String): Boolean =
        extra.any { it.name.equals(extraName, ignoreCase = true) && it.isRequired } ||
            extraRequired.any { it.equals(extraName, ignoreCase = true) }

    fun optionsFor(extraName: String): List<String> =
        extra.firstOrNull { it.name.equals(extraName, ignoreCase = true) }?.options.orEmpty()
}

@Serializable
data class StremioExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int = 1,
)

/**
 * What a catalog returns per entry: enough to draw a cover and a title, and nothing else.
 *
 * The full [StremioMeta] arrives later from `/meta`, which is why nothing here is trusted to be
 * complete — a catalog that omits `type` is asking us to fall back to the type we requested.
 */
@Serializable
data class StremioMetaPreview(
    val id: String = "",
    val type: String? = null,
    val name: String = "",
    val poster: String? = null,
    val posterShape: String? = null,
    val description: String? = null,
    val releaseInfo: JsonElement? = null,
    val imdbRating: JsonElement? = null,
    val genres: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
)

@Serializable
data class StremioMeta(
    val id: String = "",
    val type: String? = null,
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: JsonElement? = null,
    val runtime: String? = null,
    val country: String? = null,
    val language: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val videos: List<StremioVideo> = emptyList(),
    /**
     * The IMDb id for this title, where the addon knows one.
     *
     * Optional in the spec and absent more often than not, but when it is there it is the cheapest
     * possible answer to *what would a subtitle addon call this* — no search, no title matching, no
     * chance of landing on the wrong show.
     */
    @SerialName("imdb_id")
    val imdbId: String? = null,
    val behaviorHints: StremioMetaHints? = null,
) {
    /** `genres` is current, `genre` is what older addons send; some send both. */
    val allGenres: List<String>
        get() = (genres + genre).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

@Serializable
data class StremioMetaHints(
    val defaultVideoId: String? = null,
)

@Serializable
data class StremioVideo(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val overview: String? = null,
    val description: String? = null,
)

/**
 * One way to watch one video.
 *
 * A stream is a union type that the spec expresses as "set exactly one of these fields": [url]
 * for something we can hand a player, [infoHash] for a torrent, and [ytId] / [externalUrl] for
 * things only another app can open. We keep all four so the mapper can decide, rather than
 * silently dropping a shape here and leaving a mystery downstream.
 */
@Serializable
data class StremioStream(
    val url: String? = null,
    val ytId: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val externalUrl: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val sources: List<String> = emptyList(),
    val subtitles: List<StremioSubtitle> = emptyList(),
    val behaviorHints: StremioStreamHints? = null,
)

@Serializable
data class StremioSubtitle(
    val id: String? = null,
    val url: String = "",
    val lang: String = "",
)

@Serializable
data class StremioStreamHints(
    val notWebReady: Boolean = false,
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoSize: Long? = null,
    val proxyHeaders: StremioProxyHeaders? = null,
)

@Serializable
data class StremioProxyHeaders(
    val request: Map<String, String> = emptyMap(),
    val response: Map<String, String> = emptyMap(),
)

@Serializable
data class StremioCatalogResponse(val metas: List<StremioMetaPreview> = emptyList())

@Serializable
data class StremioMetaResponse(val meta: StremioMeta? = null)

@Serializable
data class StremioStreamResponse(val streams: List<StremioStream> = emptyList())

@Serializable
data class StremioSubtitleResponse(val subtitles: List<StremioSubtitle> = emptyList())

/**
 * A JSON value that some addons send as a string and others as a number, read as text either way.
 *
 * `releaseInfo` is the usual offender: `"2011-2019"` from one addon, `2011` from the next. Typing
 * it as either one makes the other addon fail to parse its whole response over a year label.
 */
internal fun JsonElement?.primitiveText(): String? =
    (this as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

private fun JsonPrimitive.contentOrNullSafe(): String? = content.takeIf { it != "null" }
