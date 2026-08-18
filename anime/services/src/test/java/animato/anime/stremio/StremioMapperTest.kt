package animato.anime.stremio

import eu.kanade.tachiyomi.animesource.model.SAnime
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Stremio's answers, turned into the app's own vocabulary.
 *
 * The cases here are the ones a device would report as something other than what they are. An
 * episode list ordered wrongly reads as "the source is broken". A stream we cannot play, listed
 * anyway, reads as "the video failed" only after someone taps it. A torrent whose file index is
 * dropped plays the wrong file in a season pack — a real video, of the wrong episode, which is
 * the hardest kind of wrong to report.
 */
@Execution(ExecutionMode.CONCURRENT)
class StremioMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `an entry address survives ids that contain colons`() {
        val url = StremioMapper.entryUrl("series", "kitsu:1234")
        url shouldBe "series:kitsu:1234"
        StremioMapper.parseEntryUrl(url) shouldBe ("series" to "kitsu:1234")

        StremioMapper.parseEntryUrl("series:tt0944947:1:1") shouldBe ("series" to "tt0944947:1:1")
    }

    @Test
    fun `an address with nothing on one side of the colon is not an address`() {
        StremioMapper.parseEntryUrl("tt0944947") shouldBe null
        StremioMapper.parseEntryUrl(":tt0944947") shouldBe null
        StremioMapper.parseEntryUrl("series:") shouldBe null
    }

    @Test
    fun `episodes come back in watch order with specials last`() {
        val meta = StremioMeta(
            id = "tt0944947",
            type = "series",
            name = "Test Series",
            videos = listOf(
                StremioVideo(id = "tt:2:1", season = 2, episode = 1, title = "Second season opener"),
                StremioVideo(id = "tt:0:1", season = 0, episode = 1, title = "A special"),
                StremioVideo(id = "tt:1:2", season = 1, episode = 2, title = "Second"),
                StremioVideo(id = "tt:1:1", season = 1, episode = 1, title = "First"),
            ),
        )

        val episodes = StremioMapper.toEpisodes(meta, "series")

        episodes.map { it.url } shouldBe listOf("series:tt:1:1", "series:tt:1:2", "series:tt:2:1", "series:tt:0:1")
        // Numbering is a running index, not the episode field: across seasons that field restarts
        // at 1, and two episodes sharing a number are two the app cannot tell apart.
        episodes.map { it.episode_number } shouldBe listOf(1f, 2f, 3f, 4f)
        episodes.first().name shouldBe "S1:E1 · First"
    }

    @Test
    fun `a film with no videos still has something to play`() {
        val meta = StremioMeta(id = "tt1254207", type = "movie", name = "Big Buck Bunny")

        val episodes = StremioMapper.toEpisodes(meta, "movie")

        episodes.size shouldBe 1
        // A film's stream is fetched under the entry's own id, so that is the address kept.
        episodes.single().url shouldBe "movie:tt1254207"
        episodes.single().name shouldBe "Big Buck Bunny"
        episodes.single().episode_number shouldBe 1f
    }

    @Test
    fun `a direct url stream becomes a playable video`() {
        val videos = StremioMapper.toVideos(
            listOf(
                StremioStream(
                    url = "https://cdn.test/video.mp4",
                    name = "Test addon",
                    description = "Some.Release.1080p.WEB\n👤 42 💾 2.1 GB",
                    subtitles = listOf(StremioSubtitle(url = "https://cdn.test/sub.srt", lang = "ar")),
                ),
            ),
        )

        val video = videos.single()
        video.videoUrl shouldBe "https://cdn.test/video.mp4"
        // Both halves of the label matter when choosing, and the picker is one line tall.
        video.videoTitle shouldBe "Test addon · Some.Release.1080p.WEB · 👤 42 💾 2.1 GB"
        video.resolution shouldBe 1080
        video.subtitleTracks.single().lang shouldBe "ar"
    }

    @Test
    fun `a torrent stream becomes a magnet the player already understands`() {
        val videos = StremioMapper.toVideos(
            listOf(
                StremioStream(
                    infoHash = "abc123",
                    fileIdx = 3,
                    name = "Torrentio 720p",
                    sources = listOf("tracker:udp://tracker.test:80", "dht:abc123"),
                ),
            ),
        )

        val url = videos.single().videoUrl
        url.startsWith("magnet:?xt=urn:btih:abc123") shouldBe true
        // The player reads the file to open out of `index=`, and that is exactly what fileIdx
        // says — a dropped index plays the wrong episode of a season pack, not an error.
        url.contains("&index=3") shouldBe true
        url.contains("&tr=udp%3A%2F%2Ftracker.test%3A80") shouldBe true
        // `dht:` entries are peer hints the torrent server finds on its own, not trackers.
        url.contains("dht") shouldBe false
    }

    @Test
    fun `a torrent with no file index still names a file`() {
        val url = StremioMapper.toVideos(listOf(StremioStream(infoHash = "abc123", name = "x"))).single().videoUrl
        url.contains("&index=0") shouldBe true
    }

    @Test
    fun `streams only another app could open are left out rather than listed`() {
        val videos = StremioMapper.toVideos(
            listOf(
                StremioStream(ytId = "dQw4w9WgXcQ", name = "YouTube"),
                StremioStream(externalUrl = "https://netflix.test/watch/1", name = "Netflix"),
                StremioStream(url = "https://cdn.test/ok.mp4", name = "Playable"),
            ),
        )

        // Listing them would fill the quality picker with entries that fail only once tapped.
        videos.map { it.videoTitle } shouldBe listOf("Playable")
    }

    @Test
    fun `4K is read as a resolution even when it is spelled in words`() {
        fun resolutionOf(label: String) =
            StremioMapper.toVideos(listOf(StremioStream(url = "https://x.test/v", name = label))).single().resolution

        resolutionOf("Release 2160p") shouldBe 2160
        resolutionOf("Release 4K HDR") shouldBe 2160
        resolutionOf("Release 720p") shouldBe 720
        resolutionOf("Release of unknown size") shouldBe null
    }

    @Test
    fun `proxy headers ride along so the stream can actually be fetched`() {
        val video = StremioMapper.toVideos(
            listOf(
                StremioStream(
                    url = "https://cdn.test/v.m3u8",
                    name = "Guarded",
                    behaviorHints = StremioStreamHints(
                        proxyHeaders = StremioProxyHeaders(request = mapOf("Referer" to "https://site.test/")),
                    ),
                ),
            ),
        ).single()

        video.headers?.get("Referer") shouldBe "https://site.test/"
    }

    @Test
    fun `a closed year range is read as a finished series when no status is sent`() {
        StremioMapper.toSAnime(
            StremioMeta(id = "x", name = "Ended", releaseInfo = JsonPrimitive("2011-2019")),
            "series",
        ).status shouldBe SAnime.COMPLETED

        StremioMapper.toSAnime(
            StremioMeta(id = "x", name = "Running", releaseInfo = JsonPrimitive("2011-")),
            "series",
        ).status shouldBe SAnime.UNKNOWN

        StremioMapper.toSAnime(
            StremioMeta(id = "x", name = "Running", status = "Continuing"),
            "series",
        ).status shouldBe SAnime.ONGOING
    }

    @Test
    fun `releaseInfo is read whether the addon sends a string or a number`() {
        // Typing this field as either one makes the other addon fail to parse its whole response
        // over a year label, which is a source that appears entirely empty.
        val asNumber = json.decodeFromString<StremioMeta>("""{"id":"x","name":"n","releaseInfo":2011}""")
        val asString = json.decodeFromString<StremioMeta>("""{"id":"x","name":"n","releaseInfo":"2011"}""")

        asNumber.releaseInfo.primitiveText() shouldBe "2011"
        asString.releaseInfo.primitiveText() shouldBe "2011"
    }

    @Test
    fun `a metadata-only addon is never asked for streams`() {
        // Cinemeta's shape: it knows what everything is called and has no video at all. Asking it
        // for a stream returns an empty answer indistinguishable from "nothing is available",
        // which is how a title ends up saying the video failed when no video was ever offered.
        val cinemeta = json.decodeFromString<StremioManifest>(
            """{"id":"c","name":"Cinemeta","resources":["catalog","meta"],"types":["movie","series"]}""",
        )

        cinemeta.canServe("meta", "movie", "tt123") shouldBe true
        cinemeta.canServe("stream", "movie", "tt123") shouldBe false
    }

    @Test
    fun `a stream addon is asked only for the types and ids it declared`() {
        // The object form of a resource narrows further than the manifest does: this one streams
        // films and series, and only for IMDb ids.
        val provider = json.decodeFromString<StremioManifest>(
            """
            {
              "id":"p","name":"Provider",
              "resources":[{"name":"stream","types":["movie","series"],"idPrefixes":["tt"]}],
              "types":["movie","series","tv"]
            }
            """.trimIndent(),
        )

        provider.canServe("stream", "movie", "tt123") shouldBe true
        provider.canServe("stream", "series", "tt123:1:1") shouldBe true
        // Declared out of scope, so asking would spend a request to be told nothing.
        provider.canServe("stream", "movie", "kitsu:1") shouldBe false
        provider.canServe("stream", "tv", "tt123") shouldBe false
    }

    @Test
    fun `an addon that declares no narrowing answers for anything it serves`() {
        // An absent list is no constraint at all, not an empty one — read the other way round,
        // every unnarrowed addon would be filtered out and nothing would ever be asked.
        val open = json.decodeFromString<StremioManifest>(
            """{"id":"o","name":"Open","resources":["stream"]}""",
        )

        open.canServe("stream", "movie", "tt123") shouldBe true
        open.canServe("stream", "anime", "kitsu:9") shouldBe true
        open.canServe("catalog", "movie", "tt123") shouldBe false
    }

    @Test
    fun `a catalog entry keeps the type it was requested under when it omits its own`() {
        val anime = StremioMapper.toSAnime(StremioMetaPreview(id = "tt1", name = "Untyped"), "series")
        anime.url shouldBe "series:tt1"
    }

    @Test
    fun `episode release dates are read from any of the shapes that arrive`() {
        fun dateOf(released: String?) = StremioMapper.toEpisodes(
            StremioMeta(id = "x", name = "n", videos = listOf(StremioVideo(id = "v", released = released))),
            "series",
        ).single().date_upload

        (dateOf("2011-04-17T00:00:00.000Z") > 0L) shouldBe true
        (dateOf("2011-04-17") > 0L) shouldBe true
        (dateOf("2011") > 0L) shouldBe true
        // An unreadable date costs the episode its date and nothing else.
        dateOf("not a date") shouldBe 0L
        dateOf(null) shouldBe 0L
    }
}
