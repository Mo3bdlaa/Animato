package animato.anime.track.anilist

import animato.anime.track.anilist.dto.ALAnimeListQueryResult
import animato.anime.track.anilist.dto.ALAnimeSearchResult
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * What AniList sends back, and what it turns into.
 *
 * One line is deliberately not covered: the description is run through `htmlDecode`, which calls
 * Android's `Html.fromHtml`, and there is no implementation of that on a JVM. Rather than mock the
 * platform to reach one line of Mihon's own helper, the fixtures leave the description out — so
 * what is tested here is everything a wrong field number or a wrong status would break, and the
 * decode is left to the function Mihon already uses everywhere else.
 *
 * Two things here fail silently rather than loudly, which is why they are tested. A status number
 * that maps to the wrong word is still a valid status — nothing throws, the entry simply says
 * "dropped" when the user marked it "watching", and it is written to AniList that way on the next
 * update. And a field AniList omits for one anime but not another turns a decode into an exception
 * on that one anime only, which is the kind of thing that reaches a user before it reaches a test.
 */
@Execution(ExecutionMode.CONCURRENT)
class AnimeAnilistDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads a search result`() {
        val results = json.decodeFromString<ALAnimeSearchResult>(SEARCH_RESPONSE)
            .data.page.media
            .map { it.toTrackSearch() }

        val result = results.single()
        result.remote_id shouldBe 20L
        result.title shouldBe "NARUTO"
        result.total_episodes shouldBe 220L
        result.publishing_status shouldBe "FINISHED"
        result.publishing_type shouldBe "TV"
        result.tracking_url shouldBe "https://anilist.co/anime/20"
        // The main studio, not the production committee listed beside it.
        result.authors shouldBe listOf("Studio Pierrot")
    }

    /**
     * AniList leaves out what it does not know rather than sending nulls, and it does so per anime:
     * an unaired one has no episode count, a short has no studios, a placeholder has no description.
     */
    @Test
    fun `reads a search result with everything optional missing`() {
        val result = json.decodeFromString<ALAnimeSearchResult>(SPARSE_SEARCH_RESPONSE)
            .data.page.media
            .single()
            .toTrackSearch()

        result.remote_id shouldBe 999L
        result.title shouldBe "Unaired Thing"
        result.total_episodes shouldBe 0L
        result.summary shouldBe ""
        result.authors shouldBe emptyList()
        result.start_date shouldBe ""
    }

    @Test
    fun `reads a list entry`() {
        val track = json.decodeFromString<ALAnimeListQueryResult>(LIST_RESPONSE)
            .data.page.mediaList
            .single()
            .toTrack()

        track.remote_id shouldBe 20L
        track.library_id shouldBe 12345L
        track.last_episode_seen shouldBe 57.0
        track.total_episodes shouldBe 220L
        track.score shouldBe 85.0
        track.private shouldBe true
        track.status shouldBe AnimeAnilist.WATCHING
    }

    /**
     * The numbers are Aniyomi's, and they are not arbitrary: they go into the database and into
     * every backup. Changing one would make an Aniyomi backup restore with a different status than
     * it was saved with, without anything reporting a problem.
     */
    @Test
    fun `keeps the status numbers Aniyomi wrote`() {
        AnimeAnilist.WATCHING shouldBe 11L
        AnimeAnilist.COMPLETED shouldBe 2L
        AnimeAnilist.ON_HOLD shouldBe 3L
        AnimeAnilist.DROPPED shouldBe 4L
        AnimeAnilist.PLAN_TO_WATCH shouldBe 15L
        AnimeAnilist.REWATCHING shouldBe 16L
    }

    @Test
    fun `maps every status both ways`() {
        val roundTrip = mapOf(
            "CURRENT" to AnimeAnilist.WATCHING,
            "COMPLETED" to AnimeAnilist.COMPLETED,
            "PAUSED" to AnimeAnilist.ON_HOLD,
            "DROPPED" to AnimeAnilist.DROPPED,
            "PLANNING" to AnimeAnilist.PLAN_TO_WATCH,
            "REPEATING" to AnimeAnilist.REWATCHING,
        )

        roundTrip.forEach { (apiStatus, trackStatus) ->
            val fromApi = json.decodeFromString<ALAnimeListQueryResult>(listResponse(status = apiStatus))
                .data.page.mediaList
                .single()
                .toTrack()

            fromApi.status shouldBe trackStatus
            AnimeTrack.create(2L).also { it.status = trackStatus }.toApiStatus() shouldBe apiStatus
        }
    }

    private fun listResponse(status: String) = LIST_RESPONSE.replace("\"CURRENT\"", "\"$status\"")
}

private val SEARCH_RESPONSE = """
{"data":{"Page":{"media":[{
  "id":20,
  "title":{"userPreferred":"NARUTO"},
  "coverImage":{"large":"https://example/cover.jpg"},
  "format":"TV",
  "status":"FINISHED",
  "episodes":220,
  "averageScore":79,
  "startDate":{"year":2002,"month":10,"day":3},
  "studios":{"edges":[
    {"isMain":true,"node":{"name":"Studio Pierrot"}},
    {"isMain":false,"node":{"name":"TV Tokyo"}}
  ]}
}]}}}
""".trimIndent()

private val SPARSE_SEARCH_RESPONSE = """
{"data":{"Page":{"media":[{
  "id":999,
  "title":{"userPreferred":"Unaired Thing"},
  "coverImage":{"large":"https://example/cover.jpg"}
}]}}}
""".trimIndent()

private val LIST_RESPONSE = """
{"data":{"Page":{"mediaList":[{
  "id":12345,
  "status":"CURRENT",
  "scoreRaw":85,
  "progress":57,
  "private":true,
  "startedAt":{"year":2024,"month":1,"day":5},
  "completedAt":{"year":null,"month":null,"day":null},
  "media":{
    "id":20,
    "title":{"userPreferred":"NARUTO"},
    "coverImage":{"large":"https://example/cover.jpg"},
    "format":"TV",
    "status":"FINISHED",
    "episodes":220,
    "studios":{"edges":[{"isMain":true,"node":{"name":"Studio Pierrot"}}]}
  }
}]}}}
""".trimIndent()
