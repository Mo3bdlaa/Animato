package animato.anime.track.bangumi

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.bangumi.BangumiInterceptor
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMCollectionResponse
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMSearchResult
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.CacheControl
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/**
 * Bangumi's anime half.
 *
 * Bangumi's models are shared with Mihon's rather than rewritten: its endpoints answer about
 * "subjects" and the field is `eps` whether those are episodes or volumes, so only the conversions
 * to a track differ — and the search filter, which asks for type 2, anime.
 */
class AnimeBangumiApi(
    private val trackId: Long,
    client: OkHttpClient,
    interceptor: BangumiInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val body = collectionBody(track).toString().toRequestBody()
        // Answers 202 Accepted with no body.
        authClient.newCall(
            POST(collectionUrl(track), body = body, headers = headersOf("Content-Type", APP_JSON)),
        ).awaitSuccess()
        track
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val body = collectionBody(track).toString().toRequestBody()
        val request = Request.Builder()
            .url(collectionUrl(track))
            .patch(body)
            .headers(headersOf("Content-Type", APP_JSON))
            .build()
        // Answers 204 No Content.
        authClient.newCall(request).awaitSuccess()
        track
    }

    /**
     * Bangumi's search has been marked experimental since 2022 and is still the only one there is.
     */
    suspend fun searchAnime(search: String): List<AnimeTrackSearch> = withIOContext {
        val body = buildJsonObject {
            put("keyword", search)
            put("sort", "match")
            putJsonObject("filter") {
                putJsonArray("type") {
                    // 2 is anime (动画).
                    add(ANIME_SUBJECT_TYPE)
                }
            }
        }
            .toString()
            .toRequestBody()

        with(json) {
            authClient.newCall(
                POST(
                    "$API_URL/v0/search/subjects?limit=20",
                    body = body,
                    headers = headersOf("Content-Type", APP_JSON),
                ),
            )
                .awaitSuccess()
                .parseAs<BGMSearchResult>()
                .data
                .map { subject ->
                    AnimeTrackSearch.create(trackId).also {
                        it.remote_id = subject.id
                        // Bangumi is a Chinese site; the Chinese name is the one its users know.
                        it.title = subject.nameCn.ifBlank { subject.name }
                        it.cover_url = subject.images?.common.orEmpty()
                        it.summary = if (subject.nameCn.isNotBlank()) {
                            "作品原名：${subject.name}" + subject.summary?.let { s -> "\n${s.trim()}" }.orEmpty()
                        } else {
                            subject.summary?.trim().orEmpty()
                        }
                        it.score = subject.rating?.score ?: -1.0
                        it.tracking_url = "https://bangumi.tv/subject/${subject.id}"
                        it.total_episodes = subject.eps
                        it.start_date = subject.date.orEmpty()
                    }
                }
        }
    }

    /**
     * The user's own collection entry for this anime, or null when they do not have one.
     */
    suspend fun statusLibAnime(track: AnimeTrack, username: String): AnimeTrack? = withIOContext {
        val url = "$API_URL/v0/users/$username/collections/${track.remote_id}"
        with(json) {
            try {
                authClient.newCall(GET(url, cache = CacheControl.FORCE_NETWORK))
                    .awaitSuccess()
                    .parseAs<BGMCollectionResponse>()
                    .let {
                        track.status = it.getStatus()
                        track.last_episode_seen = it.epStatus?.toDouble() ?: 0.0
                        track.score = it.rate?.toDouble() ?: 0.0
                        track.total_episodes = it.subject?.eps?.toLong() ?: 0L
                        track
                    }
            } catch (e: HttpException) {
                // "subject is not collected by user" — an answer, not a failure.
                if (e.code == 404) null else throw e
            }
        }
    }

    private fun collectionBody(track: AnimeTrack) = buildJsonObject {
        put("type", track.toApiStatus())
        put("rate", track.score.toInt().coerceIn(0, 10))
        put("ep_status", track.last_episode_seen.toInt())
        put("private", track.private)
    }

    private fun collectionUrl(track: AnimeTrack) = "$API_URL/v0/users/-/collections/${track.remote_id}"

    private companion object {
        const val API_URL = "https://api.bgm.tv"
        const val APP_JSON = "application/json"
        const val ANIME_SUBJECT_TYPE = 2
    }
}
