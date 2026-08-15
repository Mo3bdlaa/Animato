package animato.anime.track.simkl

import android.net.Uri
import androidx.core.net.toUri
import animato.anime.track.simkl.dto.SimklOAuth
import animato.anime.track.simkl.dto.SimklSearchResult
import animato.anime.track.simkl.dto.SimklSyncResult
import animato.anime.track.simkl.dto.SimklSyncWatched
import animato.anime.track.simkl.dto.SimklUser
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

/**
 * Simkl's API.
 *
 * Simkl keeps shows, films and anime in one account but on three sets of endpoints, and a track
 * carries no field saying which it is. The type is read back out of `tracking_url`, which is why
 * [SimklSyncItem.toAnimeTrack] writes a relative one: it is the only place the answer survives.
 *
 * Progress is not a number here. Simkl records *which* episodes were watched, so moving the
 * position means removing the history and writing it again from one to the new episode — which is
 * what [updateProgress] does, and why an update is three requests rather than one.
 */
class SimklApi(private val client: OkHttpClient, interceptor: SimklInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        addToList(track, listNameFor(track))
        track
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        // A film has no episode history to rewrite, only a place on a list and a rating.
        if (mediaTypeOf(track) != "movies") {
            updateProgress(track)
        }
        addToList(track, listNameFor(track))
        updateRating(track, listNameFor(track))
        track
    }

    suspend fun searchAnime(search: String, type: String): List<AnimeTrackSearch> = withIOContext {
        val searchUrl = "$API_URL/search/$type".toUri().buildUpon()
            .appendQueryParameter("q", search)
            .appendQueryParameter("extended", "full")
            .appendQueryParameter("client_id", CLIENT_ID)
            .build()

        with(json) {
            client.newCall(GET(searchUrl.toString()))
                .awaitSuccess()
                .parseAs<List<SimklSearchResult>>()
                .map { it.toTrackSearch(type) }
        }
    }

    /**
     * The user's own entry for this title, or null when they do not have one.
     */
    suspend fun findLibAnime(track: AnimeTrack): AnimeTrack? = withIOContext {
        val payload = buildJsonArray {
            addJsonObject { put("simkl", track.remote_id) }
        }.toString().toRequestBody(jsonMime)

        val watched = with(json) {
            authClient.newCall(POST("$API_URL/sync/watched", body = payload))
                .awaitSuccess()
                .parseAs<List<SimklSyncWatched>>()
                .firstOrNull()
        } ?: return@withIOContext null

        if (watched.result != true) return@withIOContext null
        val lastWatched = watched.lastWatched ?: return@withIOContext null
        val status = watched.list ?: return@withIOContext null

        // "watched" says only that it is on a list. What is on it comes from the list itself, and
        // the date narrows that list to the part that could contain this title.
        val type = mediaTypeOf(track)
        val queryType = if (type == "tv") "shows" else type
        val url = "$API_URL/sync/all-items/$queryType/$status".toUri().buildUpon()
            .appendQueryParameter("date_from", lastWatched)
            .build()

        val typeName = if (type == "movies") "movie" else "show"
        val item = with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<SimklSyncResult>()
                .getFromType(queryType)
                ?.firstOrNull { it.getFromType(typeName).ids.simkl == track.remote_id }
        } ?: return@withIOContext null

        item.toAnimeTrack(typeName, type, status)
    }

    suspend fun getCurrentUser(): Int = withIOContext {
        with(json) {
            authClient.newCall(GET("$API_URL/users/settings"))
                .awaitSuccess()
                .parseAs<SimklUser>()
                .account.id
        }
    }

    suspend fun accessToken(code: String): SimklOAuth = withIOContext {
        val body = buildJsonObject {
            put("code", code)
            put("client_id", CLIENT_ID)
            put("client_secret", CLIENT_SECRET)
            put("redirect_uri", REDIRECT_URL)
            put("grant_type", "authorization_code")
        }.toString().toRequestBody(jsonMime)

        with(json) {
            client.newCall(POST(OAUTH_URL, body = body)).awaitSuccess().parseAs()
        }
    }

    private suspend fun addToList(track: AnimeTrack, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") { put("simkl", track.remote_id) }
                    put("to", track.toSimklStatus())
                }
            }
        }.toString().toRequestBody(jsonMime)

        authClient.newCall(POST("$API_URL/sync/add-to-list", body = payload)).awaitSuccess()
    }

    private suspend fun updateRating(track: AnimeTrack, mediaType: String) {
        val payload = buildJsonObject {
            putJsonArray(mediaType) {
                addJsonObject {
                    putJsonObject("ids") { put("simkl", track.remote_id) }
                    put("rating", track.score.toInt())
                }
            }
        }.toString().toRequestBody(jsonMime)

        // Simkl has no rating of zero; clearing one is a removal.
        val endpoint = if (track.score == 0.0) "$API_URL/sync/ratings/remove" else "$API_URL/sync/ratings"
        authClient.newCall(POST(endpoint, body = payload)).awaitSuccess()
    }

    private suspend fun updateProgress(track: AnimeTrack) {
        authClient.newCall(
            POST("$API_URL/sync/history/remove", body = progressBody(track, add = false)),
        ).awaitSuccess()
        authClient.newCall(
            POST("$API_URL/sync/history", body = progressBody(track, add = true)),
        ).awaitSuccess()
    }

    private fun progressBody(track: AnimeTrack, add: Boolean) = buildJsonObject {
        putJsonArray("shows") {
            addJsonObject {
                putJsonObject("ids") { put("simkl", track.remote_id) }
                putJsonArray("seasons") {
                    addJsonObject {
                        // Everything Animato tracks is one continuous run of episodes, which is
                        // season one as far as Simkl is concerned.
                        put("number", 1)
                        if (add) {
                            putJsonArray("episodes") {
                                for (number in 1..track.last_episode_seen.toInt()) {
                                    addJsonObject { put("number", number) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }.toString().toRequestBody(jsonMime)

    /**
     * Which of Simkl's three catalogues this track belongs to, read back out of its tracking url.
     */
    private fun mediaTypeOf(track: AnimeTrack): String =
        track.tracking_url.substringAfter("/").substringBefore("/")

    /**
     * The name that same catalogue goes by in a sync payload, where anime and tv are both shows.
     */
    private fun listNameFor(track: AnimeTrack): String =
        if (mediaTypeOf(track) == "movies") "movies" else "shows"

    companion object {
        const val CLIENT_ID = "aa62a7da32518aae5d5049a658b87fa4837c3b739e06ed250b315aab6af82b0e"
        private const val CLIENT_SECRET = "2bec9c1d0c00a1e9b0e9e096a71f88d555a6f52da7923df07906df3b21351783"

        private const val BASE_URL = "https://simkl.com"
        private const val API_URL = "https://api.simkl.com"
        private const val OAUTH_URL = "$API_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"
        const val POSTERS_URL = "https://simkl.in/posters/"

        /**
         * Aniyomi's, and it has to be: the client id above is registered with Simkl against this
         * exact address, and Simkl refuses a redirect it does not recognise. Registering an Animato
         * client would let this become `animato://`, and is a two-line change when there is one.
         *
         * The consequence is that `AnimeTrackLoginActivity` answers an `aniyomi://` link, so on a
         * phone with both apps installed the system asks which should handle it. Signing in to
         * Simkl is rare enough, and the alternative is not signing in at all.
         */
        private const val REDIRECT_URL = "aniyomi://simkl-auth"

        fun authUrl(): Uri = LOGIN_URL.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URL)
            .build()
    }
}
