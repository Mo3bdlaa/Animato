package animato.anime.torznab

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * One Torznab endpoint somebody added.
 *
 * Jackett and Prowlarr both publish this, which is why there is one implementation and not two:
 * the endpoint is the standard Sonarr and Radarr are configured with, so the address and key are
 * exactly what somebody already has copied out of their own dashboard.
 */
@Serializable
data class TorznabIndexer(
    /** The Torznab endpoint, ending at `/api` — see [TorznabUrls.normalizeBase]. */
    val url: String,
    val name: String,
    val apiKey: String,
)

/**
 * The indexers the user has added.
 *
 * ## What adding validates
 *
 * A `t=caps` request, which is the one question every Torznab endpoint answers and the only way to
 * tell a working indexer from a wrong address or a wrong key. It also returns the categories, so
 * the same request that proves the thing works is the one that fills the category chips.
 */
class TorznabIndexerStore(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    private val stored = preferenceStore.getString(PREF_KEY, "")

    private val _indexers = MutableStateFlow(read())
    val indexers: StateFlow<List<TorznabIndexer>> = _indexers.asStateFlow()

    suspend fun add(rawUrl: String, apiKey: String, name: String): Result<TorznabIndexer> = withIOContext {
        val base = TorznabUrls.normalizeBase(rawUrl)
        if (base.isEmpty() || !base.startsWith("http")) {
            return@withIOContext Result.failure(IllegalArgumentException(INVALID_URL))
        }
        if (apiKey.isBlank()) {
            return@withIOContext Result.failure(IllegalArgumentException(NO_KEY))
        }
        if (_indexers.value.any { it.url.equals(base, ignoreCase = true) }) {
            return@withIOContext Result.failure(IllegalStateException(ALREADY_ADDED))
        }

        val caps = try {
            val response = network.client.newCall(GET(TorznabUrls.caps(base, apiKey))).awaitSuccess()
            TorznabFeed.parseCaps(response.body.string())
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Torznab indexer unreachable: $base" }
            return@withIOContext Result.failure(IOException(UNREACHABLE))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Torznab caps unreadable: $base" }
            return@withIOContext Result.failure(IllegalStateException(NOT_TORZNAB))
        }

        // An endpoint that answers with no categories at all is answering, but not as a Torznab
        // indexer — a wrong API key on Jackett returns an error document with a 200, which parses
        // into nothing rather than failing.
        if (caps.categories.isEmpty()) {
            return@withIOContext Result.failure(IllegalStateException(NOT_TORZNAB))
        }

        val indexer = TorznabIndexer(
            url = base,
            name = name.trim().takeIf { it.isNotEmpty() } ?: hostOf(base),
            apiKey = apiKey.trim(),
        )
        write(_indexers.value + indexer)
        Result.success(indexer)
    }

    fun remove(url: String) {
        write(_indexers.value.filterNot { it.url.equals(url, ignoreCase = true) })
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    private fun read(): List<TorznabIndexer> {
        val raw = stored.get()
        if (raw.isEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<TorznabIndexer>>(raw) }
            .getOrElse {
                logcat(LogPriority.ERROR, it) { "Stored Torznab indexers could not be read" }
                emptyList()
            }
    }

    private fun write(indexers: List<TorznabIndexer>) {
        stored.set(json.encodeToString(indexers))
        _indexers.value = indexers
    }

    companion object {
        private const val PREF_KEY = "animato_torznab_indexers"

        const val INVALID_URL = "That does not look like a Torznab address"
        const val NO_KEY = "Enter the API key from your indexer"
        const val ALREADY_ADDED = "That indexer is already here"
        const val UNREACHABLE = "Could not reach that address — is the indexer running?"
        const val NOT_TORZNAB = "That answered, but not like a Torznab indexer — check the API key"
    }
}
