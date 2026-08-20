package animato.anime.torznab

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * Torznab's XML, and the several shapes it arrives in.
 *
 * Every case here is one real indexers actually produce. The format is RSS with a namespaced
 * extension and no two of them fill it in the same way — one sends `magneturl`, one puts the magnet
 * in `<link>`, one sends only an info hash, and Jackett passes release titles through with
 * characters that are not legal XML. A parser that assumed one shape would work against whichever
 * indexer it was written for and silently return nothing for the rest.
 */
@Execution(ExecutionMode.CONCURRENT)
class TorznabFeedTest {

    private fun feed(items: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
          <channel>$items</channel>
        </rss>
    """.trimIndent()

    @Test
    fun `a magnet is found wherever the indexer put it`() {
        val releases = TorznabFeed.parseSearch(
            feed(
                """
                <item>
                  <title>Release A</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:aaaa1111" />
                </item>
                <item>
                  <title>Release B</title>
                  <link>magnet:?xt=urn:btih:bbbb2222</link>
                </item>
                <item>
                  <title>Release C</title>
                  <torznab:attr name="infohash" value="cccc3333" />
                </item>
                """.trimIndent(),
            ),
        )

        releases.map { it.title } shouldBe listOf("Release A", "Release B", "Release C")
        // The one built from a bare hash still comes out as a magnet the player can read.
        releases[2].magnet shouldContain "magnet:?xt=urn:btih:cccc3333"
        releases[2].magnet shouldContain "dn=Release%20C"
    }

    @Test
    fun `a release with nothing to play is left out rather than listed`() {
        val releases = TorznabFeed.parseSearch(
            feed(
                """
                <item>
                  <title>Only a torrent file</title>
                  <link>https://indexer.test/download/abc.torrent</link>
                </item>
                <item>
                  <title>Playable</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:dddd4444" />
                </item>
                """.trimIndent(),
            ),
        )

        // A row that fails on tap is the least diagnosable failure this app has, so it is not
        // offered at all.
        releases.map { it.title } shouldBe listOf("Playable")
    }

    @Test
    fun `size, seeders and category are read from either place they live`() {
        val releases = TorznabFeed.parseSearch(
            feed(
                """
                <item>
                  <title>With elements</title>
                  <size>1073741824</size>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:eeee5555" />
                  <torznab:attr name="seeders" value="42" />
                  <torznab:attr name="category" value="5070" />
                </item>
                <item>
                  <title>With attrs</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:ffff6666" />
                  <torznab:attr name="size" value="500" />
                </item>
                """.trimIndent(),
            ),
        )

        releases[0].sizeBytes shouldBe 1073741824L
        releases[0].seeders shouldBe 42
        releases[0].category shouldBe "5070"

        releases[1].sizeBytes shouldBe 500L
        // Absent rather than empty: an indexer that omits an attr omits the element entirely.
        releases[1].seeders shouldBe 0
        releases[1].category shouldBe null
    }

    @Test
    fun `a title with characters that are not legal XML does not lose the whole feed`() {
        // Jackett passes release names through as the tracker wrote them, ampersand and all. A
        // strict parser rejects the document, which reads as an indexer returning nothing while it
        // works perfectly in every other client.
        val releases = TorznabFeed.parseSearch(
            feed(
                """
                <item>
                  <title>Show S01 [1080p] R&B & Friends</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:aaaa7777" />
                </item>
                <item>
                  <title>The next one</title>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:bbbb8888" />
                </item>
                """.trimIndent(),
            ),
        )

        releases.size shouldBe 2
        releases[1].title shouldBe "The next one"
    }

    @Test
    fun `a release is identified by its hash, so two indexers offering it agree`() {
        val a = TorznabRelease("Name one", "magnet:?xt=urn:btih:ABCD1234", null, 0, 0, 0)
        val b = TorznabRelease("Name two", "magnet:?xt=urn:btih:abcd1234", null, 0, 0, 0)

        // Same torrent, differently named and differently cased upstream. One row in the library.
        a.id shouldBe b.id
        a.id shouldBe "abcd1234"

        // No hash to read: the title is all there is, which is better than nothing to key on.
        TorznabRelease("Bare", "not-a-magnet", null, 0, 0, 0).id shouldBe "Bare"
    }

    @Test
    fun `dates are read in both of the shapes indexers send`() {
        val releases = TorznabFeed.parseSearch(
            feed(
                """
                <item>
                  <title>RFC 822</title>
                  <pubDate>Mon, 04 Aug 2025 12:00:00 +0000</pubDate>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:1111aaaa" />
                </item>
                <item>
                  <title>ISO 8601</title>
                  <pubDate>2025-08-04T12:00:00Z</pubDate>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:2222bbbb" />
                </item>
                <item>
                  <title>Nonsense</title>
                  <pubDate>whenever</pubDate>
                  <torznab:attr name="magneturl" value="magnet:?xt=urn:btih:3333cccc" />
                </item>
                """.trimIndent(),
            ),
        )

        releases[0].publishedAt shouldBe releases[1].publishedAt
        (releases[0].publishedAt > 0) shouldBe true
        // An unreadable date costs that row its date and nothing else.
        releases[2].publishedAt shouldBe 0L
    }

    @Test
    fun `capabilities give the subcategories, which is where anime actually lives`() {
        val caps = TorznabFeed.parseCaps(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <caps>
              <categories>
                <category id="5000" name="TV">
                  <subcat id="5070" name="Anime" />
                  <subcat id="5040" name="HD" />
                </category>
                <category id="2000" name="Movies" />
              </categories>
            </caps>
            """.trimIndent(),
        )

        caps.categories.map { it.id } shouldBe listOf("5000", "5070", "5040", "2000")
        // Named under their parent, because "Anime" alone says nothing about which shelf it is on
        // when two categories both have one.
        caps.categories[1].name shouldBe "TV · Anime"
    }

    @Test
    fun `an answer with no categories is not a Torznab indexer`() {
        // What a wrong API key looks like on Jackett: an error document, served with a 200.
        TorznabFeed.parseCaps("<error code=\"100\" description=\"Invalid API Key\" />")
            .categories shouldBe emptyList()
    }
}
