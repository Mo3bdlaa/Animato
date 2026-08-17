package animato.app.discover

import androidx.compose.runtime.Immutable
import animato.domain.content.ContentType
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

/** One cover in a metadata rail: something that exists in the world, not in anybody's library. */
@Immutable
data class MetadataItem(
    val key: String,
    val title: String,
    val coverUrl: String?,
    val caption: String?,
    val contentType: ContentType,
)

/**
 * The three questions Discover opens with, and which mediums each one can be asked about.
 *
 * Seasons are an anime idea and AniList models them that way — `season` is not a field on a manga —
 * so *This season* has one medium where the others have two. Stating that here is what lets the
 * screen build one rail per medium per question instead of asking for a rail that cannot exist.
 */
enum class MetadataRail(val media: Set<ContentType>) {
    // Spelled out rather than sharing a constant: a top-level `val` is initialised with the file's
    // facade class, and an enum constructor runs with the enum's, so the shared one is null half
    // the time depending on which is touched first.
    TRENDING(setOf(ContentType.ANIME, ContentType.MANGA)),
    THIS_SEASON(setOf(ContentType.ANIME)),
    TOP_RATED(setOf(ContentType.ANIME, ContentType.MANGA)),
}

/**
 * What the world is watching and reading, independent of what anyone has installed.
 *
 * ## Why this exists
 *
 * Discover used to ask the pinned sources for their popular and latest pages, which means a fresh
 * install — no sources, because Animato ships with none — opened on a screen with nothing on it and
 * a line of text explaining that. The first thing anybody saw was an empty room.
 *
 * These rails come from AniList's public GraphQL API instead. They work on a phone that has never
 * installed an extension, which is the whole point: you can look before you have committed to
 * anything. Nothing here can be opened *from* here — a title without a source is not a thing you
 * can read — so tapping one searches the sources that are installed, and says so when there are
 * none.
 *
 * ## Why AniList and not two APIs
 *
 * One endpoint answers for both anime and manga with the same query shape and the same fields,
 * which is what keeps this file short and the two halves genuinely symmetrical. Jikan would have
 * meant a different URL per medium, no season concept for manga at all, and two sets of rate
 * limits. `isAdult: false` is applied server-side, so nothing has to be filtered back out here.
 *
 * ## What is *not* here
 *
 * Nothing is cached across launches. These are three requests on opening one screen, they are
 * ordinary GraphQL POSTs with the app's normal client behind them, and a stale trending list is
 * worse than a fresh one that took a second. If that turns out to be wrong on a slow connection the
 * answer is a cache, not a longer file.
 */
class MetadataCatalog(
    private val network: NetworkHelper = Injekt.get(),
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * One rail, for one medium.
     *
     * Throws nothing: a rail that fails comes back empty, because three rails at the top of a
     * screen are decoration for the one below them and none of them is worth an error dialog.
     */
    suspend fun fetch(
        rail: MetadataRail,
        contentType: ContentType,
        limit: Int = PER_PAGE,
    ): List<MetadataItem> = withIOContext {
        // Seasons are an anime idea and AniList models them that way — `season` is not a field on a
        // manga. Rather than sending a query that quietly matches nothing, the manga half of this
        // rail simply does not exist, and the screen draws whatever the anime half returned.
        if (rail == MetadataRail.THIS_SEASON && contentType == ContentType.MANGA) {
            return@withIOContext emptyList()
        }
        runCatching { request(rail, contentType, limit) }.getOrDefault(emptyList())
    }

    private suspend fun request(rail: MetadataRail, contentType: ContentType, limit: Int): List<MetadataItem> {
        val variables = buildJsonObject {
            put("type", if (contentType == ContentType.ANIME) "ANIME" else "MANGA")
            put("perPage", limit)
            when (rail) {
                MetadataRail.TRENDING -> put("sort", JsonPrimitive("TRENDING_DESC").asList())
                MetadataRail.TOP_RATED -> put("sort", JsonPrimitive("SCORE_DESC").asList())
                MetadataRail.THIS_SEASON -> {
                    put("sort", JsonPrimitive("POPULARITY_DESC").asList())
                    val today = LocalDate.now()
                    put("season", currentSeason(today.monthValue))
                    put("seasonYear", today.year)
                }
            }
        }

        val body = buildJsonObject {
            put("query", if (rail == MetadataRail.THIS_SEASON) SEASON_QUERY else SORTED_QUERY)
            put("variables", variables)
        }

        val request = POST(
            url = ENDPOINT,
            body = body.toString().toRequestBody(JSON_MEDIA_TYPE),
        )

        val response = with(json) {
            network.client.newCall(request).awaitSuccess().parseAs<AniListResponse>()
        }

        return response.data?.page?.media.orEmpty().mapNotNull { it.toItem(contentType) }
    }

    private fun AniListMedia.toItem(contentType: ContentType): MetadataItem? {
        val name = title.userPreferred ?: title.romaji ?: title.english ?: return null
        return MetadataItem(
            key = "${contentType.name.lowercase()}-anilist-$id",
            title = name,
            coverUrl = coverImage?.large,
            // A percentage is what AniList stores; a ten-point score is what everyone reads. No
            // colour and no star glyph — the rating is a caption, not a badge.
            caption = averageScore?.let { "%.1f".format(it / 10.0) } ?: format?.readable(),
            contentType = contentType,
        )
    }

    private companion object {
        const val ENDPOINT = "https://graphql.anilist.co"
        const val PER_PAGE = 24

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * The two shapes of query, kept apart rather than made one with nullable arguments.
         *
         * A `season` variable that is null on two of three rails would still be declared, and
         * AniList's schema treats a declared-but-null season as a filter that matches nothing on
         * some sort combinations. Two literals are less clever and always right.
         */
        val SORTED_QUERY = """
            query (${'$'}type: MediaType, ${'$'}sort: [MediaSort], ${'$'}perPage: Int) {
              Page(perPage: ${'$'}perPage) {
                media(type: ${'$'}type, sort: ${'$'}sort, isAdult: false) {
                  id
                  title { userPreferred romaji english }
                  coverImage { large }
                  averageScore
                  format
                }
              }
            }
        """.trimIndent()

        val SEASON_QUERY = """
            query (${'$'}type: MediaType, ${'$'}sort: [MediaSort], ${'$'}season: MediaSeason,
                   ${'$'}seasonYear: Int, ${'$'}perPage: Int) {
              Page(perPage: ${'$'}perPage) {
                media(type: ${'$'}type, sort: ${'$'}sort, season: ${'$'}season,
                      seasonYear: ${'$'}seasonYear, isAdult: false) {
                  id
                  title { userPreferred romaji english }
                  coverImage { large }
                  averageScore
                  format
                }
              }
            }
        """.trimIndent()

        /** AniList's seasons, which start in December rather than in January. */
        fun currentSeason(month: Int): String = when (month) {
            12, 1, 2 -> "WINTER"
            3, 4, 5 -> "SPRING"
            6, 7, 8 -> "SUMMER"
            else -> "FALL"
        }

        fun JsonPrimitive.asList() = JsonArray(listOf(this))
    }
}

/** `TV_SHORT` is not a word. Neither is `ONE_SHOT`. */
private fun String.readable(): String =
    split('_').joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

@Serializable
private data class AniListResponse(val data: AniListData? = null)

@Serializable
private data class AniListData(@SerialName("Page") val page: AniListPage? = null)

@Serializable
private data class AniListPage(val media: List<AniListMedia> = emptyList())

@Serializable
private data class AniListMedia(
    val id: Int,
    val title: AniListTitle,
    val coverImage: AniListCover? = null,
    val averageScore: Int? = null,
    val format: String? = null,
)

@Serializable
private data class AniListTitle(
    val userPreferred: String? = null,
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
private data class AniListCover(val large: String? = null)
