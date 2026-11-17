package animato.app.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StoreKeyTest {

    @Test
    fun `a store is recognised under the address it was saved with`() {
        val listed = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json"
        storeKey(listed) shouldBe storeKey("https://github.com/yuzono/anime-repo/raw/repo/index.pb")
        storeKey(listed) shouldBe storeKey("https://raw.githubusercontent.com/yuzono/anime-repo/repo/repo.json")
        storeKey(listed) shouldBe
            storeKey("https://raw.githubusercontent.com/Yuzono/Anime-Repo/refs/heads/repo/index.min.json")
    }

    @Test
    fun `different repositories stay different`() {
        storeKey("https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json") shouldBe "yuzono/anime-repo"
        (
            storeKey(
                "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
            ) == "yuzono/anime-repo"
            ) shouldBe
            false
        storeKey("https://example.test/stores/a/index.min.json") shouldBe "example.test/stores/a"
    }
}
