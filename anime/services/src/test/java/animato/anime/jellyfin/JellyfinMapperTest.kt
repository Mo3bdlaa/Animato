package animato.anime.jellyfin

import animato.anime.content.EntryForm
import animato.anime.content.SourceProgress
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.SAnime
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * A server's answers, turned into the app's own vocabulary.
 *
 * The cases here are the ones that would reach a device as something other than what they are: an
 * episode list ordered wrongly reads as a broken server, a film described as episode one reads as
 * our data model leaking, and a music library appearing as a category reads as an app that does not
 * know what it is for.
 */
@Execution(ExecutionMode.CONCURRENT)
class JellyfinMapperTest {

    private val base = "https://media.example.test"

    private fun episode(id: String, season: Int?, number: Int?, name: String = "") = JellyfinItem(
        id = id,
        name = name,
        type = JellyfinMapper.TYPE_EPISODE,
        indexNumber = number,
        parentIndexNumber = season,
    )

    @Test
    fun `an address survives the round trip and names its type`() {
        val url = JellyfinMapper.entryUrl(JellyfinMapper.TYPE_SERIES, "a1b2c3")
        JellyfinMapper.parseEntryUrl(url) shouldBe (JellyfinMapper.TYPE_SERIES to "a1b2c3")

        // Nothing on one side of the colon is not an address.
        JellyfinMapper.parseEntryUrl("a1b2c3") shouldBe null
        JellyfinMapper.parseEntryUrl(":a1b2c3") shouldBe null
        JellyfinMapper.parseEntryUrl("Movie:") shouldBe null
    }

    @Test
    fun `a film is a single and everything else arrives in parts`() {
        JellyfinMapper.formOf("Movie:abc") shouldBe EntryForm.Single
        JellyfinMapper.formOf("Series:abc") shouldBe EntryForm.Serial
        // Nothing on a personal server is live, so an unreadable address is a serial like the rest
        // of the app assumes rather than anything more exciting.
        JellyfinMapper.formOf("nonsense") shouldBe EntryForm.Serial
    }

    @Test
    fun `episodes are numbered in watch order and listed newest first`() {
        val episodes = JellyfinMapper.toEpisodes(
            listOf(
                episode("e2", season = 1, number = 2),
                episode("e1", season = 1, number = 1),
                // Season two restarts at episode one, which is the reason numbering follows the
                // sorted position rather than the server's own IndexNumber.
                episode("s2e1", season = 2, number = 1),
            ),
        )

        episodes.map { it.url } shouldBe listOf("Episode:s2e1", "Episode:e2", "Episode:e1")
        // Newest first on screen, ascending in number: the sorter reads position 0 as the newest.
        episodes.map { it.episode_number } shouldBe listOf(3f, 2f, 1f)
    }

    @Test
    fun `specials are numbered after the run rather than before it`() {
        val episodes = JellyfinMapper.toEpisodes(
            listOf(
                episode("special", season = 0, number = 1),
                episode("e1", season = 1, number = 1),
                episode("e2", season = 1, number = 2),
            ),
        )

        // Season 0 is where every server files extras. Numbering them first would renumber the
        // whole series and put episode one at number two.
        episodes.first().url shouldBe "Episode:special"
        episodes.first().episode_number shouldBe 3f
    }

    @Test
    fun `an episode is named by its position and its title, and copes with neither`() {
        JellyfinMapper.toEpisodes(listOf(episode("e", 2, 5, "The One With The Thing")))
            .single().name shouldBe "S2:E5 · The One With The Thing"

        JellyfinMapper.toEpisodes(listOf(episode("e", null, null))).single().name shouldBe "Episode"
    }

    @Test
    fun `a film's one row is named after the film`() {
        val film = JellyfinItem(id = "f", name = "Arrival", type = JellyfinMapper.TYPE_MOVIE)
        val row = JellyfinMapper.toSingleEpisode(film)

        // Not "Episode 1", which is a sentence about our storage rather than about the film.
        row.name shouldBe "Arrival"
        row.episode_number shouldBe 1f
        row.url shouldBe "Movie:f"
    }

    @Test
    fun `a film is fetched once and a series is asked again`() {
        val film = JellyfinMapper.toSAnime(
            JellyfinItem(id = "f", name = "Arrival", type = JellyfinMapper.TYPE_MOVIE),
            base,
        )
        film.update_strategy shouldBe AnimeUpdateStrategy.ONLY_FETCH_ONCE
        // A film is finished by definition, which is more useful than "unknown" and always true.
        film.status shouldBe SAnime.COMPLETED

        val series = JellyfinMapper.toSAnime(
            JellyfinItem(id = "s", name = "Severance", type = JellyfinMapper.TYPE_SERIES, status = "Continuing"),
            base,
        )
        series.update_strategy shouldBe AnimeUpdateStrategy.ALWAYS_UPDATE
        series.status shouldBe SAnime.ONGOING
    }

    @Test
    fun `a cover carries the image tag, so a replaced one is fetched`() {
        val item = JellyfinItem(
            id = "abc",
            name = "Thing",
            type = JellyfinMapper.TYPE_SERIES,
            imageTags = mapOf("Primary" to "tag123"),
        )

        val cover = JellyfinMapper.toSAnime(item, base).thumbnail_url!!
        cover shouldContain "/Items/abc/Images/Primary"
        // Without the tag the app would keep showing the old artwork until something cleared its
        // cache, which is not a thing anybody knows to do.
        cover shouldContain "tag=tag123"
        cover shouldContain "maxHeight="
    }

    @Test
    fun `the director is preferred and the studio fills in`() {
        val withDirector = JellyfinItem(
            id = "a",
            type = JellyfinMapper.TYPE_MOVIE,
            people = listOf(
                JellyfinPerson(name = "Amy Adams", type = "Actor"),
                JellyfinPerson(name = "Denis Villeneuve", type = "Director"),
            ),
            studios = listOf(JellyfinNamed(name = "Paramount")),
        )
        JellyfinMapper.toSAnime(withDirector, base).author shouldBe "Denis Villeneuve"
        JellyfinMapper.toSAnime(withDirector, base).artist shouldBe "Amy Adams"

        // A series usually credits no director, and the row would otherwise be blank for every
        // series on the server.
        val series = JellyfinItem(id = "b", type = JellyfinMapper.TYPE_SERIES, studios = listOf(JellyfinNamed("Apple")))
        JellyfinMapper.toSAnime(series, base).author shouldBe "Apple"
    }

    @Test
    fun `music and books are not offered as categories, and a mixed library still is`() {
        val views = listOf(
            JellyfinItem(id = "1", name = "Films", collectionType = "movies"),
            JellyfinItem(id = "2", name = "Music", collectionType = "music"),
            JellyfinItem(id = "3", name = "Books", collectionType = "books"),
            // Jellyfin leaves this unset for a mixed library. Dropping those would hide the shelf
            // of anyone who never told their server what kind of thing they were storing.
            JellyfinItem(id = "4", name = "Everything else", collectionType = null),
        )

        JellyfinMapper.videoLibraries(views).map { it.name } shouldBe listOf("Films", "Everything else")
    }

    @Test
    fun `what the server already watched is carried across`() {
        val watched = JellyfinMapper.toEpisodes(
            listOf(
                episode("e1", 1, 1).copy(userData = JellyfinUserData(played = true)),
                // Ticks are hundred-nanosecond units: 90 seconds is 900,000,000 of them.
                episode("e2", 1, 2).copy(
                    userData = JellyfinUserData(playbackPositionTicks = 900_000_000L),
                ),
                episode("e3", 1, 3),
            ),
        ).associateBy { it.url }

        SourceProgress.seenIn(watched.getValue("Episode:e1").memo) shouldBe true
        SourceProgress.positionIn(watched.getValue("Episode:e2").memo) shouldBe 90_000L
        // Nothing said means nothing carried, which is what every other source in the app looks
        // like — and is what stops this from writing over local progress with an empty answer.
        SourceProgress.seenIn(watched.getValue("Episode:e3").memo) shouldBe false
        SourceProgress.positionIn(watched.getValue("Episode:e3").memo) shouldBe 0L
    }

    @Test
    fun `a film carries its own watched state too`() {
        val film = JellyfinItem(
            id = "f",
            name = "Arrival",
            type = JellyfinMapper.TYPE_MOVIE,
            userData = JellyfinUserData(played = true, playbackPositionTicks = 12_000_000L),
        )

        val memo = JellyfinMapper.toSingleEpisode(film).memo
        SourceProgress.seenIn(memo) shouldBe true
        SourceProgress.positionIn(memo) shouldBe 1_200L
    }
}
