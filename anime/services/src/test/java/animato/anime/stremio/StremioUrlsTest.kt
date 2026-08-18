package animato.anime.stremio

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The address of every question we ask an addon.
 *
 * Worth its own file because every mistake available here is silent. A wrongly trimmed base URL
 * still resolves — to a *differently configured* addon, which answers happily with the wrong
 * catalogue. A colon left unencoded in an episode id still forms a legal path, which some servers
 * route and others 404. And a space written as `+` instead of `%20` is a search that simply finds
 * nothing. None of these throw; they all just return the wrong thing.
 */
@Execution(ExecutionMode.CONCURRENT)
class StremioUrlsTest {

    @Test
    fun `every way a person can paste an addon reduces to the same base`() {
        val expected = "https://v3-cinemeta.strem.io"
        StremioUrls.normalizeBase("https://v3-cinemeta.strem.io/manifest.json") shouldBe expected
        StremioUrls.normalizeBase("https://v3-cinemeta.strem.io/") shouldBe expected
        StremioUrls.normalizeBase("https://v3-cinemeta.strem.io") shouldBe expected
        StremioUrls.normalizeBase("  https://v3-cinemeta.strem.io/manifest.json  ") shouldBe expected
        StremioUrls.normalizeBase("stremio://v3-cinemeta.strem.io/manifest.json") shouldBe expected
        StremioUrls.normalizeBase("v3-cinemeta.strem.io/manifest.json") shouldBe expected
    }

    @Test
    fun `a configured addon keeps its configuration path`() {
        // The configuration lives in the path, so trimming to the host would hand back a
        // different addon that answers with a different set of providers.
        StremioUrls.normalizeBase("https://torrentio.strem.fun/providers=yts,eztv/manifest.json") shouldBe
            "https://torrentio.strem.fun/providers=yts,eztv"
    }

    @Test
    fun `an empty address stays empty rather than becoming a scheme`() {
        StremioUrls.normalizeBase("") shouldBe ""
        StremioUrls.normalizeBase("   ") shouldBe ""
    }

    @Test
    fun `a catalog with no extras is a plain path`() {
        StremioUrls.catalog("https://addon.test/manifest.json", "series", "top") shouldBe
            "https://addon.test/catalog/series/top.json"
    }

    @Test
    fun `extras are a query string living inside a path segment`() {
        StremioUrls.catalog(
            base = "https://addon.test",
            type = "movie",
            id = "bbbcatalog",
            extra = linkedMapOf("search" to "game of thrones", "skip" to "100"),
        ) shouldBe "https://addon.test/catalog/movie/bbbcatalog/search=game%20of%20thrones&skip=100.json"
    }

    @Test
    fun `blank extras are dropped instead of sent empty`() {
        StremioUrls.catalog(
            base = "https://addon.test",
            type = "movie",
            id = "top",
            extra = linkedMapOf("search" to "", "genre" to "Comedy"),
        ) shouldBe "https://addon.test/catalog/movie/top/genre=Comedy.json"
    }

    @Test
    fun `ids carrying colons are encoded, because most of them do`() {
        StremioUrls.meta("https://addon.test", "series", "kitsu:1234") shouldBe
            "https://addon.test/meta/series/kitsu%3A1234.json"

        // Every episode of every series looks like this.
        StremioUrls.stream("https://addon.test", "series", "tt0944947:1:1") shouldBe
            "https://addon.test/stream/series/tt0944947%3A1%3A1.json"
    }

    @Test
    fun `encoding follows encodeURIComponent, not form encoding`() {
        // A space is %20 and never +; the sub-delims JavaScript leaves alone are left alone.
        "a b".encodeUriComponent() shouldBe "a%20b"
        "tt123!~*'()-_.".encodeUriComponent() shouldBe "tt123!~*'()-_."
        "a/b?c#d&e=f".encodeUriComponent() shouldBe "a%2Fb%3Fc%23d%26e%3Df"
    }

    @Test
    fun `non-ascii titles survive as utf-8 percent escapes`() {
        "日".encodeUriComponent() shouldBe "%E6%97%A5"
    }

    @Test
    fun `the manifest hangs off whatever base was given`() {
        StremioUrls.manifest("https://addon.test/config=1") shouldBe "https://addon.test/config=1/manifest.json"
        StremioUrls.manifest("https://addon.test/manifest.json") shouldBe "https://addon.test/manifest.json"
    }
}
