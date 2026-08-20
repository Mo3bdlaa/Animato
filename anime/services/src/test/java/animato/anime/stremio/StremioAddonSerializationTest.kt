package animato.anime.stremio

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * That an addon can still be written to disk and read back.
 *
 * ## The crash this is here for
 *
 * [StremioAddon] carried a `private companion object` for two releases, added to hold one string
 * constant. The serialization plugin puts `serializer()` on a serializable class's companion, so a
 * private companion makes the generated `Companion` field private — and the class that calls it,
 * `StremioAddonStore`, is a different one. That compiles, ships, and throws `IllegalAccessError`
 * the first time anything saves an addon. Reported from a device as a crash on opening the addons
 * screen, which is where the refresh happens.
 *
 * The test lives in its own class deliberately: the failure is about access *across* classes, and
 * a round trip performed inside `StremioAddon` itself would not have caught it.
 */
@Execution(ExecutionMode.CONCURRENT)
class StremioAddonSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an addon survives being written and read back`() {
        val addon = StremioAddon(
            url = "https://v3-cinemeta.strem.io/manifest.json",
            manifest = StremioManifest(
                id = "com.linvo.cinemeta",
                name = "Cinemeta",
                types = listOf("movie", "series"),
            ),
        )

        val restored = json.decodeFromString<List<StremioAddon>>(
            json.encodeToString(listOf(addon)),
        )

        restored.single().url shouldBe addon.url
        restored.single().manifest.name shouldBe "Cinemeta"
    }

    @Test
    fun `a live TV addon is still recognised after a round trip`() {
        // The constant that caused all this. It is read on a restored addon, which is the path
        // the Sources tab takes on every launch.
        val addon = StremioAddon(
            url = "https://iptv.example.test/manifest.json",
            manifest = StremioManifest(id = "test.iptv", name = "Channels", types = listOf("tv")),
        )

        val restored = json.decodeFromString<StremioAddon>(json.encodeToString(addon))

        restored.servesLiveTv shouldBe true
        restored.servesOnDemand shouldBe false
    }
}
