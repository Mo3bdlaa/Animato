package animato.anime.track.kitsu

import androidx.core.net.toUri
import animato.anime.track.kitsu.dto.KitsuAddAnimeResult
import animato.anime.track.kitsu.dto.KitsuAnimeListResult
import animato.anime.track.kitsu.dto.KitsuAnimeSearchResult
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.kitsu.KitsuDateHelper
import eu.kanade.tachiyomi.data.track.kitsu.KitsuInterceptor
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchResult
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Kitsu's anime half.
 *
 * Searching goes through Algolia rather than Kitsu's own API — Kitsu hands out a short-lived
 * Algolia key and expects the search to be made there, which is why every search is two requests.
 * The facet filter is the only part that differs from Mihon's: `kind:anime`, and `episodeCount`
 * among the attributes to return instead of `chapterCount`.
 *
 * The interceptor is Mihon's, so the token and its refresh are shared.
 */
class AnimeKitsuApi(private val client: OkHttpClient, interceptor: KitsuInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: AnimeTrack, userId: String): AnimeTrack = withIOContext {
        val data = buildJsonObject {
            putJsonObject("data") {
                put("type", "libraryEntries")
                putJsonObject("attributes") {
                    put("status", track.toApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("private", track.private)
                }
                putJsonObject("relationships") {
                    putJsonObject("user") {
                        putJsonObject("data") {
                            put("id", userId)
                            put("type", "users")
                        }
                    }
                    putJsonObject("media") {
                        putJsonObject("data") {
                            put("id", track.remote_id)
                            put("type", "anime")
                        }
                    }
                }
            }
        }

        with(json) {
            authClient.newCall(
                POST(
                    "${BASE_URL}library-entries",
                    headers = headersOf("Content-Type", VND_API_JSON),
                    body = data.toString().toRequestBody(VND_JSON_MEDIA_TYPE),
                ),
            )
                .awaitSuccess()
                .parseAs<KitsuAddAnimeResult>()
                .let {
                    // Kitsu identifies the *library entry*, not the anime, from here on.
                    track.remote_id = it.data.id
                    track
                }
        }
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val data = buildJsonObject {
            putJsonObject("data") {
                put("type", "libraryEntries")
                put("id", track.remote_id)
                putJsonObject("attributes") {
                    put("status", track.toApiStatus())
                    put("progress", track.last_episode_seen.toInt())
                    put("ratingTwenty", track.toApiScore())
                    put("startedAt", KitsuDateHelper.convert(track.started_watching_date))
                    put("finishedAt", KitsuDateHelper.convert(track.finished_watching_date))
                    put("private", track.private)
                }
            }
        }

        authClient.newCall(
            Request.Builder()
                .url("${BASE_URL}library-entries/${track.remote_id}")
                .headers(headersOf("Content-Type", VND_API_JSON))
                .patch(data.toString().toRequestBody(VND_JSON_MEDIA_TYPE))
                .build(),
        ).awaitSuccess()

        track
    }

    suspend fun removeLibAnime(track: DomainAnimeTrack) = withIOContext {
        authClient.newCall(
            DELETE(
                "${BASE_URL}library-entries/${track.remoteId}",
                headers = headersOf("Content-Type", VND_API_JSON),
            ),
        ).awaitSuccess()
        Unit
    }

    suspend fun searchAnime(query: String): List<AnimeTrackSearch> = withIOContext {
        with(json) {
            val key = authClient.newCall(GET(ALGOLIA_KEY_URL))
                .awaitSuccess()
                .parseAs<KitsuSearchResult>()
                .media.key

            algoliaSearch(key, query)
        }
    }

    private suspend fun algoliaSearch(key: String, query: String): List<AnimeTrackSearch> = withIOContext {
        val payload = buildJsonObject {
            put("params", "query=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}$ALGOLIA_ANIME_FILTER")
        }

        with(json) {
            // The plain client, not the authenticated one: this request goes to Algolia, and
            // Kitsu's token has no business being sent there.
            client.newCall(
                POST(
                    ALGOLIA_URL,
                    headers = headersOf(
                        "X-Algolia-Application-Id",
                        ALGOLIA_APP_ID,
                        "X-Algolia-API-Key",
                        key,
                    ),
                    body = payload.toString().toRequestBody(jsonMime),
                ),
            )
                .awaitSuccess()
                .parseAs<KitsuAnimeSearchResult>()
                .hits
                .map { it.toTrackSearch() }
        }
    }

    suspend fun findLibAnime(track: AnimeTrack, userId: String): AnimeTrack? = withIOContext {
        val url = "${BASE_URL}library-entries".toUri().buildUpon()
            .encodedQuery("filter[anime_id]=${track.remote_id}&filter[user_id]=$userId")
            .appendQueryParameter("include", "anime")
            .build()

        with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<KitsuAnimeListResult>()
                .let { if (it.isEmpty) null else it.firstToTrack() }
        }
    }

    suspend fun getLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val url = "${BASE_URL}library-entries".toUri().buildUpon()
            .encodedQuery("filter[id]=${track.remote_id}")
            .appendQueryParameter("include", "anime")
            .build()

        with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<KitsuAnimeListResult>()
                .let {
                    if (it.isEmpty) throw Exception("Could not find anime") else it.firstToTrack()
                }
        }
    }

    private companion object {
        const val BASE_URL = "https://kitsu.app/api/edge/"
        const val ALGOLIA_KEY_URL = "https://kitsu.app/api/edge/algolia-keys/media/"
        const val ALGOLIA_URL =
            "https://AWQO5J657S-dsn.algolia.net/1/indexes/production_media/query/"
        const val ALGOLIA_APP_ID = "AWQO5J657S"

        // URL-encoded: facetFilters=["kind:anime"] and the attributes worth returning.
        const val ALGOLIA_ANIME_FILTER =
            "&facetFilters=%5B%22kind%3Aanime%22%5D&attributesToRetrieve=" +
                "%5B%22synopsis%22%2C%22averageRating%22%2C%22canonicalTitle%22%2C%22episodeCount%22%2C%22" +
                "posterImage%22%2C%22startDate%22%2C%22subtype%22%2C%22endDate%22%2C%20%22id%22%5D"

        const val VND_API_JSON = "application/vnd.api+json"
        val VND_JSON_MEDIA_TYPE = VND_API_JSON.toMediaType()
    }
}
