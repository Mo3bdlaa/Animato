package animato.anime.track.shikimori

import animato.anime.track.shikimori.dto.SMAnime
import animato.anime.track.shikimori.dto.SMAnimeSearchResult
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriInterceptor
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAddMangaResponse
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * Shikimori's anime half.
 *
 * Reads go through GraphQL and writes through `/v2/user_rates`, which is the split Shikimori
 * itself has — and the split Mihon moved to. The donor still reads over REST; this follows Mihon,
 * because a deprecated endpoint is not something to inherit on purpose.
 *
 * There is no query for "is this on my list", so the anime is asked for by id with its `userRate`
 * included: absent means not on the list.
 */
class AnimeShikimoriApi(
    private val trackId: Long,
    client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: AnimeTrack, userId: String): AnimeTrack = withIOContext {
        val payload = buildJsonObject {
            putJsonObject("user_rate") {
                put("user_id", userId)
                put("target_id", track.remote_id)
                put("target_type", "Anime")
                put("episodes", track.last_episode_seen.toInt())
                put("score", track.score.toInt())
                put("status", track.toShikimoriStatus())
            }
        }

        with(json) {
            authClient.newCall(
                POST("$API_URL/v2/user_rates", body = payload.toString().toRequestBody(jsonMime)),
            )
                .awaitSuccess()
                .parseAs<SMAddMangaResponse>()
                .let { track.library_id = it.id }
        }
        track
    }

    /**
     * Shikimori has no separate update: posting a rate for an anime already rated replaces it.
     */
    suspend fun updateLibAnime(track: AnimeTrack, userId: String): AnimeTrack = addLibAnime(track, userId)

    suspend fun deleteLibAnime(track: DomainAnimeTrack) = withIOContext {
        authClient.newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}")).awaitSuccess()
        Unit
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> = withIOContext {
        val query = $$"""
        |query($query: String) {
            |animes(search: $query, limit: 20) {
                |id
                |name
                |episodes
                |kind
                |poster {
                    |mainUrl
                |}
                |score
                |url
                |status
                |airedOn {
                    |date
                |}
                |description
                |personRoles {
                    |person {
                        |name
                    |}
                    |rolesEn
                |}
            |}
        |}
        """.trimMargin()

        with(json) {
            postGraphql(query) { put("query", search) }
                .data.animes
                .map { it.toTrackSearch(trackId) }
        }
    }

    /**
     * The anime with the user's own rate attached, or null when it is not on their list.
     *
     * [isRefresh] is the difference between "not on the list yet" and "was on the list and is
     * gone". On a refresh the second is worth reporting; on a bind it is the ordinary case.
     */
    suspend fun findLibAnime(track: AnimeTrack, isRefresh: Boolean = false): AnimeTrackSearch? = withIOContext {
        val query = $$"""
        |query($id: String) {
            |animes(ids: $id, limit: 1) {
                |id
                |url
                |name
                |episodes
                |userRate {
                    |id
                    |episodes
                    |status
                    |score
                |}
            |}
        |}
        """.trimMargin()

        with(json) {
            val anime: SMAnime? = postGraphql(query) { put("id", track.remote_id.toString()) }
                .data.animes
                .firstOrNull()

            if (isRefresh && anime?.isOnUserList != true) return@with null

            anime?.toTrackSearch(trackId)
        }
    }

    private suspend fun postGraphql(
        query: String,
        variables: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): SMAnimeSearchResult {
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables", variables)
        }
        return with(json) {
            authClient.newCall(POST(GRAPHQL_API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .parseAs<SMAnimeSearchResult>()
        }
    }

    private companion object {
        const val BASE_URL = "https://shikimori.one"
        const val API_URL = "$BASE_URL/api"
        const val GRAPHQL_API_URL = "$BASE_URL/api/graphql"
    }
}
