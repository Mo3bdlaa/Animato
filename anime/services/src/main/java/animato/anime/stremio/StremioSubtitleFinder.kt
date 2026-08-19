package animato.anime.stremio

import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Subtitles for an episode that did not come from Stremio.
 *
 * Subtitle addons answer by universal id — OpenSubtitles declares `idPrefixes: ["tt"]`, so it
 * knows IMDb and nothing else. Entries from ordinary extensions have no such id: an extension
 * returns a URL on its own site and nothing that anyone else would recognise. So the subtitle work
 * reached Stremio entries only, which is the smaller half of most people's libraries and not the
 * half they watch anime in.
 *
 * The bridge is a search. A metadata addon — Cinemeta, in practice — resolves a title to an IMDb
 * id, and checking that by hand against four anime titles put the right id first every time. No
 * third-party mapping file, no tracker account, nothing to keep up to date: the same addon that is
 * already installed for its catalogue answers this too.
 *
 * ## What it will and will not claim
 *
 * A title has to match exactly once punctuation and case are gone before the id is used. A near
 * match is not good enough here in a way it would be for a suggestion rail: subtitles for the
 * wrong show are not a worse guess, they are wrong text over the video. Anything short of certain
 * returns nothing and the episode keeps whatever its own source offered.
 *
 * The season is read out of the title when it says one, and assumed to be the first when it does
 * not. That is right for most anime, whose seasons are separate entries named accordingly, and
 * wrong for the sources that number every episode of a long series from one — where it produces
 * subtitles for a different episode of the right show. It is the weakest link here and the reason
 * the tracks stay a list to choose from rather than something applied automatically.
 */
class StremioSubtitleFinder(
    private val addonStore: StremioAddonStore = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    /** Resolved ids, kept for the session: the same anime is looked up once per episode otherwise. */
    private val resolved = ConcurrentHashMap<String, String>()

    /**
     * Whether asking is worth the round trip at all — no subtitle addon, no reason to search.
     */
    fun isUsable(): Boolean = addonStore.addons.value.any { it.manifest.serves(RESOURCE_SUBTITLES) }

    suspend fun subtitlesFor(animeTitle: String, episodeNumber: Float): List<Track> {
        if (!isUsable()) return emptyList()
        val imdbId = runCatching { resolve(animeTitle) }.getOrNull() ?: return emptyList()

        val season = seasonIn(animeTitle)
        val episode = episodeNumber.toInt().coerceAtLeast(1)
        val videoId = "$imdbId:$season:$episode"

        val providers = addonStore.addons.value.filter {
            it.manifest.canServe(RESOURCE_SUBTITLES, TYPE_SERIES, imdbId)
        }
        if (providers.isEmpty()) return emptyList()

        return coroutineScope {
            providers.map { provider ->
                async {
                    runCatching {
                        val url = StremioUrls.subtitles(provider.url, TYPE_SERIES, videoId)
                        val response = network.client.newCall(GET(url)).awaitSuccess()
                        with(json) { response.parseAs<StremioSubtitleResponse>() }.subtitles
                    }.getOrElse {
                        logcat(LogPriority.INFO, it) {
                            "Stremio subtitles for $videoId: ${provider.url} did not answer"
                        }
                        emptyList()
                    }
                }
            }.awaitAll().flatten().let(StremioMapper::toTracks)
        }
    }

    /**
     * The IMDb id for a title, or nothing.
     *
     * Searched against a catalogue addon rather than a fixed endpoint, because the addon is what
     * the person chose to install and hardcoding Cinemeta's address would be a source this app
     * added on their behalf.
     */
    internal suspend fun resolve(animeTitle: String): String? {
        val key = normalise(animeTitle)
        if (key.isEmpty()) return null
        resolved[key]?.let { return it }

        val catalogue = addonStore.addons.value.firstOrNull {
            it.manifest.serves(RESOURCE_CATALOG) && it.manifest.idPrefixes.any { prefix -> prefix == IMDB_PREFIX }
        } ?: return null
        val catalog = catalogue.manifest.catalogs.firstOrNull { it.type == TYPE_SERIES } ?: return null

        val url = StremioUrls.catalog(
            base = catalogue.url,
            type = catalog.type,
            id = catalog.id,
            extra = mapOf("search" to animeTitle),
        )
        val response = network.client.newCall(GET(url)).awaitSuccess()
        val metas = with(json) { response.parseAs<StremioCatalogResponse>() }.metas

        // Exact once punctuation and case are gone, or nothing. See the class note: a near match
        // here is wrong text over the video rather than a slightly worse guess.
        val match = metas.firstOrNull { normalise(it.name) == key } ?: return null
        if (!match.id.startsWith(IMDB_PREFIX)) return null
        return match.id.also { resolved[key] = it }
    }

    internal companion object {
        const val RESOURCE_SUBTITLES = "subtitles"
        const val RESOURCE_CATALOG = "catalog"
        const val TYPE_SERIES = "series"
        const val IMDB_PREFIX = "tt"

        /**
         * A season named in the title, which is where anime keeps it.
         *
         * "Season 2", "2nd Season" and "S2" are the three spellings that actually appear; anything
         * else is treated as the first season, which is what a title with no season marker almost
         * always is.
         */
        private val SEASON_PATTERNS = listOf(
            Regex("""season\s*(\d{1,2})""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,2})(?:st|nd|rd|th)\s*season""", RegexOption.IGNORE_CASE),
            Regex("""\bs(\d{1,2})\b""", RegexOption.IGNORE_CASE),
        )

        fun seasonIn(title: String): Int = SEASON_PATTERNS
            .firstNotNullOfOrNull { it.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.coerceIn(1, 99)
            ?: 1

        /**
         * Titles compared with everything but their letters and digits removed.
         *
         * An extension's title comes from whichever site it was scraped from and Cinemeta's comes
         * from IMDb, so the same show arrives as "Frieren: Beyond Journey's End" and
         * "Frieren Beyond Journeys End". Comparing them literally would match almost nothing.
         */
        fun normalise(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }
    }
}
