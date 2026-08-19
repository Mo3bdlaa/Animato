package animato.anime.iptv

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The parser, against the shapes real playlists actually arrive in.
 *
 * Worth testing properly because the format has no specification and the failures are quiet: a
 * mis-split name gives a channel called `group-title="Sport"`, and a mis-read id moves every row
 * in somebody's library the next time the playlist is refetched. Neither looks like a crash.
 */
@Execution(ExecutionMode.CONCURRENT)
class M3uParserTest {

    @Test
    fun `an ordinary playlist`() {
        val channels = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="aljazeera.qa" tvg-logo="http://logo/aj.png" group-title="News",Al Jazeera
            http://example.test/aljazeera.m3u8
            #EXTINF:-1 tvg-id="bbc1.uk" group-title="Entertainment",BBC One
            http://example.test/bbc1.m3u8
            """.trimIndent(),
        )

        channels.map { it.name } shouldBe listOf("Al Jazeera", "BBC One")
        channels.map { it.group } shouldBe listOf("News", "Entertainment")
        channels.first().logo shouldBe "http://logo/aj.png"
        channels.first().url shouldBe "http://example.test/aljazeera.m3u8"
    }

    @Test
    fun `a comma inside an attribute does not become the channel name`() {
        // The failure this guards: splitting on the first comma names the channel
        // `group-title="Movies` and loses everything after it.
        val channels = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 group-title="Movies, Drama",Cinema One
            http://example.test/one
            """.trimIndent(),
        )

        channels.single().name shouldBe "Cinema One"
        channels.single().group shouldBe "Movies, Drama"
    }

    @Test
    fun `the id survives a playlist being reordered`() {
        // Same two channels, other way round. A library keyed on position would move both.
        val first = M3uParser.parse(
            """
            #EXTINF:-1 tvg-id="a",A
            http://example.test/a
            #EXTINF:-1 tvg-id="b",B
            http://example.test/b
            """.trimIndent(),
        )
        val second = M3uParser.parse(
            """
            #EXTINF:-1 tvg-id="b",B
            http://example.test/b
            #EXTINF:-1 tvg-id="a",A
            http://example.test/a
            """.trimIndent(),
        )

        first.associate { it.id to it.name } shouldBe second.associate { it.id to it.name }
    }

    @Test
    fun `a channel with no tvg-id is identified by its address`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1,Nameless
            http://example.test/stream
            """.trimIndent(),
        )

        channels.single().id shouldBe "http://example.test/stream"
        channels.single().tvgId shouldBe null
    }

    @Test
    fun `directives between the channel and its address are ignored, except the group`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1 tvg-id="x",Channel X
            #EXTVLCOPT:http-user-agent=Mozilla
            #EXTGRP:Sport
            http://example.test/x
            """.trimIndent(),
        )

        channels.single().name shouldBe "Channel X"
        // Some providers put the group here instead of in an attribute.
        channels.single().group shouldBe "Sport"
        channels.single().url shouldBe "http://example.test/x"
    }

    @Test
    fun `an entry with no address is dropped rather than pointed at nothing`() {
        val channels = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="orphan",Orphan
            #EXTINF:-1 tvg-id="real",Real
            http://example.test/real
            """.trimIndent(),
        )

        channels.map { it.name } shouldBe listOf("Real")
    }

    @Test
    fun `the same channel twice keeps the first`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1 tvg-id="dup",Best quality
            http://example.test/1080
            #EXTINF:-1 tvg-id="dup",Lower quality
            http://example.test/480
            """.trimIndent(),
        )

        channels.single().name shouldBe "Best quality"
    }

    @Test
    fun `groups come back in the order the playlist uses them`() {
        val channels = M3uParser.parse(
            """
            #EXTINF:-1 group-title="News",A
            http://example.test/a
            #EXTINF:-1 group-title="Sport",B
            http://example.test/b
            #EXTINF:-1 group-title="News",C
            http://example.test/c
            """.trimIndent(),
        )

        M3uParser.groupsOf(channels) shouldBe listOf("News", "Sport")
    }

    @Test
    fun `nothing at all is not a crash`() {
        M3uParser.parse("") shouldBe emptyList()
        M3uParser.parse("#EXTM3U") shouldBe emptyList()
        M3uParser.parse("not a playlist") shouldBe emptyList()
    }
}
