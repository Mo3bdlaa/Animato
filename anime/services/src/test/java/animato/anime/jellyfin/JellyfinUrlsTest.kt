package animato.anime.jellyfin

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * The addresses, which are the part a server rejects silently.
 *
 * A wrong path here returns an empty list rather than an error, so a mistake reads as an empty
 * library rather than as a bug — the reason this has its own file and its own tests.
 */
@Execution(ExecutionMode.CONCURRENT)
class JellyfinUrlsTest {

    @Test
    fun `whatever was pasted becomes a base URL`() {
        // The address bar of the web UI, which is what people actually copy.
        JellyfinUrls.normalizeBase("https://media.example.test/web/index.html") shouldBe
            "https://media.example.test"
        JellyfinUrls.normalizeBase("https://media.example.test/web/") shouldBe "https://media.example.test"
        JellyfinUrls.normalizeBase("https://media.example.test/") shouldBe "https://media.example.test"

        // A bare host, which is what people type.
        JellyfinUrls.normalizeBase("media.example.test") shouldBe "https://media.example.test"

        JellyfinUrls.normalizeBase("") shouldBe ""
    }

    @Test
    fun `a path that is not the web UI survives`() {
        // An install behind a reverse proxy at a sub-path is ordinary, and trimming to the host
        // would point every single request at nothing.
        JellyfinUrls.normalizeBase("https://example.test/media/") shouldBe "https://example.test/media"
        // Emby's conventional prefix is exactly this case.
        JellyfinUrls.normalizeBase("https://example.test/emby") shouldBe "https://example.test/emby"
        JellyfinUrls.normalizeBase("https://example.test/media/web/index.html") shouldBe
            "https://example.test/media"
    }

    @Test
    fun `the catalogue asks for the fields a grid needs`() {
        val url = JellyfinUrls.items("https://s.test", "user1", parentId = "lib1", search = "the thing")

        url shouldContain "/Users/user1/Items"
        url shouldContain "ParentId=lib1"
        // A library is a tree and the grid is flat: without this, a library organised into folders
        // answers with folders.
        url shouldContain "Recursive=true"
        url shouldContain "IncludeItemTypes=Movie%2CSeries"
        // Jellyfin omits all of these by default, so a grid built without asking has no covers and
        // no descriptions.
        url shouldContain "Fields=Overview"
        url shouldContain "SearchTerm=the%20thing"
    }

    @Test
    fun `episodes are asked of the show and carry the user as a parameter`() {
        val url = JellyfinUrls.episodes("https://s.test", "user1", "series9")

        // The one thing that is easy to get backwards: items are under /Users, shows are not.
        url shouldContain "/Shows/series9/Episodes"
        url shouldContain "userId=user1"
        url shouldNotContain "/Users/user1/Shows"
    }

    @Test
    fun `the stream URL carries the token, because it leaves the app`() {
        val url = JellyfinUrls.stream("https://s.test", "item5", "tok3n")

        url shouldBe "https://s.test/Videos/item5/stream?static=true&api_key=tok3n"
    }

    @Test
    fun `ids and tokens are escaped rather than pasted`() {
        // Jellyfin ids are hex and tokens are alphanumeric, so this is defence rather than a known
        // case — but a token is user data going into a query string, and the one time it contains a
        // reserved character is the time nobody is watching.
        JellyfinUrls.stream("https://s.test", "a b", "to+ken") shouldContain "a%20b"
        JellyfinUrls.stream("https://s.test", "a b", "to+ken") shouldContain "api_key=to%2Bken"
    }
}
