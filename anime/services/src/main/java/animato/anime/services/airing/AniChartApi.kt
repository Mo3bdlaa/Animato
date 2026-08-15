package animato.anime.services.airing

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.track.anime.model.AnimeTrack

/**
 * When the next episode of an anime airs.
 *
 * An anime page shows a countdown to the next episode, and neither the source nor the library
 * knows that — the airing schedule comes from a tracker. This asks AniList, going through
 * MyAnimeList's id when that is the tracker the entry is linked to.
 *
 * Aniyomi also read Simkl's calendar here, and that branch stays absent even now that Simkl is a
 * tracker again. It downloaded three whole calendar files and sliced the raw JSON with
 * `substringAfter` — and then, whenever the entry had a MyAnimeList id, discarded all of it and
 * asked AniList, which is what the branch below already does.
 *
 * Takes tracker/track pairs rather than the screen's own track item type, so the network call
 * does not depend on the screen that triggers it.
 */
class AniChartApi {
    private val client = OkHttpClient()

    suspend fun loadAiringTime(
        anime: Anime,
        trackItems: List<Pair<Tracker, AnimeTrack?>>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        var airingEpisodeData = Pair(anime.nextEpisodeToAir, anime.nextEpisodeAiringAt)
        if (anime.status == SAnime.COMPLETED.toLong() && !manualFetch) return airingEpisodeData

        return withIOContext {
            val (tracker, track) = trackItems.firstOrNull { (tracker, track) ->
                (tracker is Anilist || tracker is MyAnimeList) && track != null
            } ?: return@withIOContext Pair(1, 0L)

            airingEpisodeData = when (tracker) {
                is Anilist -> getAnilistAiringEpisodeData(track!!.remoteId)
                is MyAnimeList -> getAnilistAiringEpisodeData(getAlIdFromMal(track!!.remoteId))
                else -> Pair(1, 0L)
            }
            return@withIOContext airingEpisodeData
        }
    }

    private suspend fun getAlIdFromMal(idMal: Long): Long {
        return withIOContext {
            val query = """
                query {
                    Media(idMal:$idMal,type: ANIME) {
                        id
                    }
                }
            """.trimMargin()

            val response = try {
                client.newCall(
                    POST(
                        "https://graphql.anilist.co",
                        body = buildJsonObject { put("query", query) }.toString()
                            .toRequestBody(jsonMime),
                    ),
                ).execute()
            } catch (e: Exception) {
                return@withIOContext 0L
            }
            return@withIOContext response.body.string().substringAfter("id\":")
                .substringBefore("}")
                .toLongOrNull() ?: 0L
        }
    }

    private suspend fun getAnilistAiringEpisodeData(id: Long): Pair<Int, Long> {
        return withIOContext {
            val query = """
                query {
                    Media(id:$id) {
                        nextAiringEpisode {
                            episode
                            airingAt
                        }
                    }
                }
            """.trimMargin()
            val response = try {
                client.newCall(
                    POST(
                        "https://graphql.anilist.co",
                        body = buildJsonObject { put("query", query) }.toString()
                            .toRequestBody(jsonMime),
                    ),
                ).execute()
            } catch (e: Exception) {
                return@withIOContext Pair(1, 0L)
            }
            val data = response.body.string()
            val episodeNumber = data.substringAfter("episode\":").substringBefore(",").toIntOrNull() ?: 1
            val airingAt = data.substringAfter("airingAt\":").substringBefore("}").toLongOrNull() ?: 0L

            return@withIOContext Pair(episodeNumber, airingAt)
        }
    }
}
