package animato.anime.stremio

import animato.anime.content.EntryForm
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
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
    fun `episodes come back newest first, numbered in watch order, specials last`() {
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

        // Newest first, because sourceOrder is this list's index and the sorter reads index 0 as
        // the newest — the convention every manga source already follows.
        episodes.map { it.url } shouldBe listOf("series:tt:0:1", "series:tt:2:1", "series:tt:1:2", "series:tt:1:1")
        // Numbering is a running index over watch order, not the episode field: across seasons
        // that field restarts at 1, and two episodes sharing a number are two the app cannot tell
        // apart. So the numbers count down as the list goes back in time.
        episodes.map { it.episode_number } shouldBe listOf(4f, 3f, 2f, 1f)
        episodes.last().name shouldBe "S1:E1 · First"
    }

    @Test
    fun `a television channel is a live row, not episode one of a series`() {
        val meta = StremioMeta(id = "tv:aljazeera", type = "tv", name = "Al Jazeera")

        val channel = StremioMapper.toSAnime(meta, "tv")
        val rows = StremioMapper.toEpisodes(meta, "tv")

        rows.single().name shouldBe "Live"
        // Nothing to re-ask for: a channel's one row is the same row forever, and an IPTV
        // catalogue is hundreds of channels being asked once per update cycle.
        channel.update_strategy shouldBe AnimeUpdateStrategy.ONLY_FETCH_ONCE
        // No air date, rather than one invented from the entry's release info.
        rows.single().date_upload shouldBe 0L
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
    fun `catalogs are named with their type, because names repeat across types`() {
        // Cinemeta's real shape: eight catalogs, three names, each name used twice. Without the
        // type the picker reads as a list of duplicates and a lookup by name finds the first.
        val cinemeta = json.decodeFromString<StremioManifest>(
            """
            {
              "id":"com.linvo.cinemeta","name":"Cinemeta",
              "resources":["catalog","meta"],"types":["movie","series"],
              "catalogs":[
                {"type":"movie","id":"top","name":"Popular"},
                {"type":"series","id":"top","name":"Popular"}
              ]
            }
            """.trimIndent(),
        )

        cinemeta.catalogs.map { it.displayName } shouldBe listOf("Popular (movie)", "Popular (series)")
    }

    @Test
    fun `a stream-only addon is kept but is not somewhere to browse`() {
        // Torrentio's shape, and the case an earlier version of the install check refused
        // outright — which left the app able to install only the half that cannot play anything.
        val torrentio = StremioAddon(
            url = "https://torrentio.strem.fun/language=arabic",
            manifest = json.decodeFromString(
                """{"id":"t","name":"Torrentio","resources":["stream"],"types":["movie","series"]}""",
            ),
        )

        torrentio.isBrowsable shouldBe false
        torrentio.manifest.canServe("stream", "movie", "tt123") shouldBe true
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
    fun `subtitles are named by language and capped per language`() {
        // What a real provider returns: the same language many times over, plus a duplicate URL.
        val tracks = StremioMapper.toTracks(
            listOf(
                StremioSubtitle(url = "https://s.test/1", lang = "ara"),
                StremioSubtitle(url = "https://s.test/1", lang = "ara"),
                StremioSubtitle(url = "https://s.test/2", lang = "ara"),
                StremioSubtitle(url = "https://s.test/3", lang = "ara"),
                StremioSubtitle(url = "https://s.test/4", lang = "ara"),
                StremioSubtitle(url = "https://s.test/5", lang = "ara"),
                StremioSubtitle(url = "https://s.test/6", lang = "eng"),
                StremioSubtitle(url = "", lang = "eng"),
            ),
        )

        // ISO 639-2 is correct and unreadable; a picker listing "ara" asks the reader to know the
        // standard. The first of a language is unnumbered, so one file reads as a language.
        tracks.map { it.lang } shouldBe listOf("Arabic", "Arabic 2", "Arabic 3", "Arabic 4", "English")
        tracks.map { it.url } shouldBe listOf(
            "https://s.test/1",
            "https://s.test/2",
            "https://s.test/3",
            "https://s.test/4",
            "https://s.test/6",
        )
    }

    @Test
    fun `a language the platform cannot name keeps its code`() {
        val tracks = StremioMapper.toTracks(
            listOf(
                StremioSubtitle(url = "https://s.test/x", lang = "zzz"),
                StremioSubtitle(url = "https://s.test/y", lang = ""),
            ),
        )
        // Better an unfamiliar code than an invented name, and better a track than none.
        tracks.map { it.lang } shouldBe listOf("zzz", "und")
    }

    @Test
    fun `a multi-season series becomes seasons, and a single-season one does not`() {
        val many = StremioMeta(
            id = "tt0944947",
            type = "series",
            name = "Test Series",
            videos = listOf(
                StremioVideo(id = "a", season = 1, episode = 1),
                StremioVideo(id = "b", season = 2, episode = 1),
                StremioVideo(id = "c", season = 0, episode = 1),
            ),
        )

        val seasons = StremioMapper.toSeasons(many, "series")

        // Specials last, for the same reason they sort last among episodes.
        seasons.map { it.title } shouldBe listOf(
            "Test Series — Season 1",
            "Test Series — Season 2",
            "Test Series — Specials",
        )
        seasons.map { it.season_number } shouldBe listOf(1.0, 2.0, 0.0)

        // One season behind a tap called "Season 1" is a worse title page than the episode list it
        // would replace, so a single-season series gets no layer at all.
        val one = StremioMeta(
            id = "x",
            type = "series",
            name = "One Season",
            videos = listOf(StremioVideo(id = "a", season = 1, episode = 1)),
        )
        StremioMapper.toSeasons(one, "series") shouldBe emptyList()
    }

    @Test
    fun `a season address survives the ids that already contain colons`() {
        val url = StremioMapper.seasonUrl("series", "kitsu:1234", 2)

        // A slash, not another colon: colons already separate the type from the id and appear
        // inside the ids, so one more would make this unparseable.
        url shouldBe "series:kitsu:1234/s2"
        StremioMapper.parseSeasonUrl(url) shouldBe ("series:kitsu:1234" to 2)
        StremioMapper.parseEntryUrl(StremioMapper.parseSeasonUrl(url)!!.first) shouldBe ("series" to "kitsu:1234")

        // An ordinary entry has no season in it and must not be read as though it had.
        StremioMapper.parseSeasonUrl("series:tt0944947") shouldBe null
    }

    @Test
    fun `a season takes only its own episodes`() {
        val meta = StremioMeta(
            id = "tt1",
            type = "series",
            name = "S",
            videos = listOf(
                StremioVideo(id = "s1e1", season = 1, episode = 1),
                StremioVideo(id = "s1e2", season = 1, episode = 2),
                StremioVideo(id = "s2e1", season = 2, episode = 1),
            ),
        )

        val second = StremioMapper.toEpisodes(meta, "series", onlySeason = 2)

        second.map { it.url } shouldBe listOf("series:s2e1")
        // Numbered from one within the season, not carried over from the whole series.
        second.single().episode_number shouldBe 1f
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

    @Test
    fun `the form of an entry is read off the type the addon gave it`() {
        // The three that are decided, and the one that decides them: the type prefix, not the
        // catalogue it came from and not what the entry turned out to hold.
        StremioMapper.formOf("tv:iptv-org-bbc-news") shouldBe EntryForm.Live
        StremioMapper.formOf("movie:tt0111161") shouldBe EntryForm.Single
        StremioMapper.formOf("series:tt0944947") shouldBe EntryForm.Serial

        // A `channel` is a list of videos despite the name — a YouTube feed, not a broadcast. It
        // is the one type whose plain-English reading points the wrong way.
        StremioMapper.formOf("channel:UCabc") shouldBe EntryForm.Serial

        // The 74 addons that declare `anime` were, until this existed, indistinguishable from
        // every other unknown label. They land where they belong anyway, which is the point of
        // defaulting to a serial rather than throwing.
        StremioMapper.formOf("anime:kitsu:1234") shouldBe EntryForm.Serial
        StremioMapper.formOf("podcast:whatever-comes-next") shouldBe EntryForm.Serial

        // A season address still carries the type at the front, so a season of a series is a
        // serial rather than an unreadable address.
        StremioMapper.formOf(StremioMapper.seasonUrl("series", "tt0944947", 2)) shouldBe EntryForm.Serial

        // Nothing to read: an address with no type is the shape the whole app already assumes.
        StremioMapper.formOf("tt0944947") shouldBe EntryForm.Serial
    }

    @Test
    fun `a category id survives a genre with a slash in it`() {
        // The catalog on its own.
        StremioMapper.parseCategoryId(StremioMapper.categoryId(3)) shouldBe (3 to null)

        // A genre inside it. Round-tripped rather than asserted as a literal string: the encoding
        // is nobody's business but the mapper's, and only the pair coming back out matters.
        val plain = StremioMapper.categoryId(0, "Comedy")
        StremioMapper.parseCategoryId(plain) shouldBe (0 to "Comedy")

        // The reason the split is on the first separator only. Addons publish these.
        val slashed = StremioMapper.categoryId(2, "Action/Adventure")
        StremioMapper.parseCategoryId(slashed) shouldBe (2 to "Action/Adventure")

        StremioMapper.parseCategoryId(StremioMapper.categoryId(1, "Sci-Fi & Fantasy")) shouldBe
            (1 to "Sci-Fi & Fantasy")

        // Nothing readable in front of the separator, and nothing readable at all.
        StremioMapper.parseCategoryId("top/Action") shouldBe null
        StremioMapper.parseCategoryId("") shouldBe null
    }
}
