package animato.anime.track.myanimelist

import androidx.core.net.toUri
import animato.anime.track.myanimelist.dto.MALAnime
import animato.anime.track.myanimelist.dto.MALAnimeSearchResult
import animato.anime.track.myanimelist.dto.MALListAnimeItem
import animato.anime.track.myanimelist.dto.MALListAnimeItemStatus
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListInterceptor
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * MyAnimeList's anime half.
 *
 * The interceptor is Mihon's, built from Mihon's own tracker, so the OAuth token and its refresh
 * are shared rather than duplicated.
 *
 * Every field name past the envelope differs — `num_episodes` against `num_chapters`,
 * `num_episodes_watched` against `num_chapters_read`, `is_rewatching` against `is_rereading` — so
 * the models are ours. Even the search envelope, whose shape is identical: Mihon's node is a
 * `MALManga` requiring `num_chapters`, and a search node carries only an id and a title.
 */
class AnimeMyAnimeListApi(
    private val trackId: Long,
    client: OkHttpClient,
    interceptor: MyAnimeListInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    /**
     * MAL's search returns ids only, so each result is a second request.
     *
     * Concurrently, because fifty sequential round trips is a search that feels broken.
     */
    suspend fun searchAnime(query: String): List<AnimeTrackSearch> = withIOContext {
        val url = "$BASE_API_URL/anime".toUri().buildUpon()
            // The API answers 400 to anything longer, so the query is cut rather than refused.
            .appendQueryParameter("q", query.take(MAX_QUERY_LENGTH))
            .appendQueryParameter("nsfw", "true")
            .build()

        with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<MALAnimeSearchResult>()
                .data
                .map { async { getAnimeDetails(it.node.id) } }
                .awaitAll()
        }
    }

    suspend fun getAnimeDetails(id: Long): AnimeTrackSearch = withIOContext {
        val url = "$BASE_API_URL/anime".toUri().buildUpon()
            .appendPath(id.toString())
            .appendQueryParameter(
                "fields",
                "id,title,synopsis,num_episodes,mean,main_picture,status,media_type,start_date",
            )
            .build()

        with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<MALAnime>()
                .let { anime ->
                    AnimeTrackSearch.create(trackId).also {
                        it.remote_id = anime.id
                        it.title = anime.title
                        it.summary = anime.synopsis
                        it.total_episodes = anime.numEpisodes
                        it.score = anime.mean
                        it.cover_url = anime.covers?.large.orEmpty()
                        it.tracking_url = "https://myanimelist.net/anime/${anime.id}"
                        it.publishing_status = anime.status.replace("_", " ")
                        it.publishing_type = anime.mediaType.replace("_", " ")
                        it.start_date = anime.startDate.orEmpty()
                    }
                }
        }
    }

    suspend fun updateItem(track: AnimeTrack): AnimeTrack = withIOContext {
        val body = FormBody.Builder()
            // A track with no status MAL understands is being watched, which is the only thing it
            // can be if the user is updating it.
            .add("status", track.toMyAnimeListStatus() ?: "watching")
            // MAL has no rewatching status; it is a flag on top of "watching".
            .add("is_rewatching", (track.status == AnimeMyAnimeList.REWATCHING).toString())
            .add("score", track.score.toString())
            .add("num_watched_episodes", track.last_episode_seen.toInt().toString())
            .apply {
                toIsoDate(track.started_watching_date)?.let { add("start_date", it) }
                toIsoDate(track.finished_watching_date)?.let { add("finish_date", it) }
            }
            .build()

        val request = Request.Builder()
            .url(animeUrl(track.remote_id))
            .put(body)
            .build()

        with(json) {
            authClient.newCall(request)
                .awaitSuccess()
                .parseAs<MALListAnimeItemStatus>()
                .let { applyListStatus(it, track) }
        }
    }

    suspend fun deleteItem(track: DomainAnimeTrack) = withIOContext {
        authClient.newCall(DELETE(animeUrl(track.remoteId))).awaitSuccess()
        Unit
    }

    /**
     * The user's own entry for this anime, or null if they have not added it.
     */
    suspend fun findListItem(track: AnimeTrack): AnimeTrack? = withIOContext {
        val url = "$BASE_API_URL/anime".toUri().buildUpon()
            .appendPath(track.remote_id.toString())
            .appendQueryParameter("fields", "num_episodes,my_list_status{start_date,finish_date}")
            .build()

        with(json) {
            authClient.newCall(GET(url.toString()))
                .awaitSuccess()
                .parseAs<MALListAnimeItem>()
                .let { item ->
                    track.total_episodes = item.numEpisodes
                    item.myListStatus?.let { applyListStatus(it, track) }
                }
        }
    }

    /**
     * Searches the user's own list rather than all of MyAnimeList.
     *
     * MAL has no endpoint for this, so the list is paged through and filtered here. That is as slow
     * as it sounds on a large list, and it is what "my:" in the search box asks for.
     */
    suspend fun findListItems(query: String, offset: Int = 0): List<AnimeTrackSearch> = withIOContext {
        val page = getListPage(offset)
        val matches = page.data
            .filter { it.node.title.contains(query, ignoreCase = true) }
            .map { async { getAnimeDetails(it.node.id) } }
            .awaitAll()

        if (page.paging.next.isNullOrBlank()) {
            matches
        } else {
            matches + findListItems(query, offset + LIST_PAGE_SIZE)
        }
    }

    private suspend fun getListPage(offset: Int): MALAnimeSearchResult = withIOContext {
        val url = "$BASE_API_URL/users/@me/animelist".toUri().buildUpon()
            .appendQueryParameter("fields", "list_status")
            .appendQueryParameter("limit", LIST_PAGE_SIZE.toString())
            .appendQueryParameter("offset", offset.toString())
            .appendQueryParameter("nsfw", "true")
            .build()

        with(json) {
            authClient.newCall(GET(url.toString())).awaitSuccess().parseAs<MALAnimeSearchResult>()
        }
    }

    private fun applyListStatus(listStatus: MALListAnimeItemStatus, track: AnimeTrack): AnimeTrack = track.also {
        it.status = if (listStatus.isRewatching) {
            AnimeMyAnimeList.REWATCHING
        } else {
            AnimeMyAnimeList.statusFrom(listStatus.status)
        }
        it.last_episode_seen = listStatus.numEpisodesWatched
        it.score = listStatus.score.toDouble()
        listStatus.startDate?.let { date -> it.started_watching_date = parseDate(date) }
        listStatus.finishDate?.let { date -> it.finished_watching_date = parseDate(date) }
    }

    private fun parseDate(isoDate: String): Long =
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time }.getOrNull() ?: 0L

    /**
     * The date in the form MAL takes, or an empty string to clear one.
     *
     * Empty rather than absent: leaving the field out keeps whatever date is already there, so
     * clearing a start date has to be said.
     */
    private fun toIsoDate(epochMillis: Long): String? {
        if (epochMillis == 0L) return ""
        return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(epochMillis) }.getOrNull()
    }

    private fun animeUrl(remoteId: Long): String = "$BASE_API_URL/anime".toUri().buildUpon()
        .appendPath(remoteId.toString())
        .appendPath("my_list_status")
        .build()
        .toString()

    private companion object {
        const val BASE_API_URL = "https://api.myanimelist.net/v2"
        const val LIST_PAGE_SIZE = 250
        const val MAX_QUERY_LENGTH = 64
    }
}
