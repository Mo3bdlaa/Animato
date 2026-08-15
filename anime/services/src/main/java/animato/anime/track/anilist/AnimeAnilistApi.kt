package animato.anime.track.anilist

import animato.anime.track.anilist.dto.ALAnimeListQueryResult
import animato.anime.track.anilist.dto.ALAnimeSearchResult
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.anilist.AnilistInterceptor
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * AniList's anime half.
 *
 * The authentication is Mihon's, not a second copy of it: the interceptor handed in here is
 * constructed from Mihon's own AniList tracker, holding the token its login screen stored. Signing
 * in once covers both halves, and there is one place where the OAuth refresh happens.
 *
 * What is here is the queries, which are genuinely different — `type: ANIME`, `episodes` instead of
 * `chapters`, studios instead of staff — and nothing else.
 */
class AnimeAnilistApi(client: OkHttpClient, interceptor: AnilistInterceptor) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder()
        .addInterceptor(interceptor)
        // AniList's documented ceiling. Mihon's client uses the same one, and the two share it.
        .rateLimit(permits = 85, period = 1.minutes)
        .build()

    suspend fun addLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val query = """
        |mutation AddAnime(${'$'}animeId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean) {
            |SaveMediaListEntry (mediaId: ${'$'}animeId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private) {
                |id
                |status
            |}
        |}
        |
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("animeId", track.remote_id)
                put("progress", track.last_episode_seen.toInt())
                put("status", track.toApiStatus())
                put("private", track.private)
            }
        }
        post(payload)
        track
    }

    suspend fun updateLibAnime(track: AnimeTrack): AnimeTrack = withIOContext {
        val query = """
        |mutation UpdateAnime(
            |${'$'}listId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean,
            |${'$'}score: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput
        |) {
            |SaveMediaListEntry(
                |id: ${'$'}listId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private,
                |scoreRaw: ${'$'}score, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt
            |) {
                |id
                |status
                |progress
            |}
        |}
        |
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("listId", track.library_id)
                put("progress", track.last_episode_seen.toInt())
                put("status", track.toApiStatus())
                put("score", track.score.toInt())
                put("startedAt", createDate(track.started_watching_date))
                put("completedAt", createDate(track.finished_watching_date))
                put("private", track.private)
            }
        }
        post(payload)
        track
    }

    suspend fun deleteLibAnime(track: DomainAnimeTrack) = withIOContext {
        val query = """
        |mutation DeleteAnime(${'$'}listId: Int) {
            |DeleteMediaListEntry(id: ${'$'}listId) {
                |deleted
            |}
        |}
        |
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("listId", track.libraryId)
            }
        }
        post(payload)
        Unit
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> = withIOContext {
        val query = """
        |query Search(${'$'}query: String) {
            |Page (perPage: 50) {
                |media(search: ${'$'}query, type: ANIME) {
                    |$ANIME_FIELDS
                |}
            |}
        |}
        |
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("query", search)
            }
        }
        with(json) {
            post(payload)
                .parseAs<ALAnimeSearchResult>()
                .data.page.media
                .map { it.toTrackSearch() }
        }
    }

    /**
     * The user's list entry for this anime, or null if they have not added it.
     */
    suspend fun findLibAnime(track: AnimeTrack, userId: Int): AnimeTrack? = withIOContext {
        val query = """
        |query (${'$'}userId: Int!, ${'$'}animeId: Int!) {
            |Page {
                |mediaList(userId: ${'$'}userId, type: ANIME, mediaId: ${'$'}animeId) {
                    |id
                    |status
                    |scoreRaw: score(format: POINT_100)
                    |progress
                    |private
                    |startedAt {
                        |year
                        |month
                        |day
                    |}
                    |completedAt {
                        |year
                        |month
                        |day
                    |}
                    |media {
                        |$ANIME_FIELDS
                    |}
                |}
            |}
        |}
        |
        """.trimMargin()
        val payload = buildJsonObject {
            put("query", query)
            putJsonObject("variables") {
                put("userId", userId)
                put("animeId", track.remote_id)
            }
        }
        with(json) {
            post(payload)
                .parseAs<ALAnimeListQueryResult>()
                .data.page.mediaList
                .firstOrNull()
                ?.toTrack()
        }
    }

    suspend fun getLibAnime(track: AnimeTrack, userId: Int): AnimeTrack =
        findLibAnime(track, userId) ?: throw Exception("Could not find anime")

    private suspend fun post(payload: JsonObject) =
        authClient.newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime))).awaitSuccess()

    /**
     * A date AniList will accept, or an explicit null to clear one.
     *
     * Sending nothing leaves the date as it was, so clearing a start date has to be said out loud.
     */
    private fun createDate(dateValue: Long): JsonObject {
        if (dateValue == 0L) {
            return buildJsonObject {
                put("year", JsonNull)
                put("month", JsonNull)
                put("day", JsonNull)
            }
        }
        val date = Instant.ofEpochMilli(dateValue).atZone(ZoneId.systemDefault())
        return buildJsonObject {
            put("year", date.year)
            put("month", date.monthValue)
            put("day", date.dayOfMonth)
        }
    }

    companion object {
        private const val API_URL = "https://graphql.anilist.co/"

        fun animeUrl(mediaId: Long): String = "https://anilist.co/anime/$mediaId"
    }
}

/**
 * The fields every anime query asks for, written once so a search result and a list entry cannot
 * drift into describing different things.
 */
private const val ANIME_FIELDS = """id
|studios {
    |edges {
        |isMain
        |node {
            |name
        |}
    |}
|}
|title {
    |userPreferred
|}
|coverImage {
    |large
|}
|format
|status
|episodes
|description
|startDate {
    |year
    |month
    |day
|}
|averageScore"""
