package animato.anime.iptv

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
import java.util.concurrent.ConcurrentHashMap

/**
 * One playlist somebody added: where it lives and what it is called.
 *
 * The channels are deliberately *not* here. A playlist is a file of ten thousand lines that its
 * provider rewrites daily; storing a parsed copy would mean a stale library and a preference entry
 * the size of the file. So this holds the address, and [M3uPlaylistStore] fetches on demand and
 * keeps the result for as long as the process lives.
 */
@Serializable
data class M3uPlaylist(
    val url: String,
    val name: String,
    /** How many channels it had when it was added — enough for a row to say something useful. */
    val channelCount: Int = 0,
)

/**
 * The playlists the user has added, and the only place they are added or removed.
 *
 * ## Why this is not the Stremio store with a different name
 *
 * An addon answers questions; a playlist is a file. There is no manifest to validate, no resources
 * to declare, nothing to ask it about — it either parses into channels or it does not, and that is
 * the whole of what "is this valid" means here. So adding one is a fetch and a parse, and the
 * failure messages are about a file rather than about a protocol.
 *
 * ## The cache, and why it is only in memory
 *
 * Channels are held per playlist for the life of the process. Long enough that browsing does not
 * refetch a ten-thousand-line file on every scroll; short enough that closing the app is how you
 * get today's list, which is the behaviour people already expect of a playlist. Persisting it
 * would be a second copy of somebody else's file, going stale on its own schedule.
 */
class M3uPlaylistStore(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    private val stored = preferenceStore.getStringSet(PREF_KEY, emptySet())

    private val _playlists = MutableStateFlow(read())
    val playlists: StateFlow<List<M3uPlaylist>> = _playlists.asStateFlow()

    private val channelCache = ConcurrentHashMap<String, List<M3uChannel>>()

    /**
     * Fetch a playlist, check it parses, and keep it.
     *
     * Named after its own `#PLAYLIST` directive where it has one and its host otherwise, because a
     * row reading `iptv-org.github.io` is more use than one reading the whole URL, and a provider
     * that bothered to name its file meant that name to be shown.
     */
    suspend fun add(url: String): Result<M3uPlaylist> = withIOContext {
        val address = url.trim()
        if (address.isBlank()) return@withIOContext Result.failure(IllegalArgumentException(EMPTY))
        if (_playlists.value.any { it.url.equals(address, ignoreCase = true) }) {
            return@withIOContext Result.failure(IllegalStateException(ALREADY_ADDED))
        }

        val text = runCatching { fetch(address) }.getOrElse {
            logcat(LogPriority.INFO, it) { "M3U playlist could not be fetched: $address" }
            return@withIOContext Result.failure(it)
        }
        val channels = M3uParser.parse(text)
        if (channels.isEmpty()) {
            return@withIOContext Result.failure(IllegalStateException(NO_CHANNELS))
        }

        val playlist = M3uPlaylist(
            url = address,
            name = nameOf(text, address),
            channelCount = channels.size,
        )
        channelCache[address] = channels
        write(_playlists.value + playlist)
        Result.success(playlist)
    }

    fun remove(url: String) {
        channelCache.remove(url)
        write(_playlists.value.filterNot { it.url == url })
    }

    /**
     * This playlist's channels, fetched if they are not already in hand.
     *
     * An empty list on failure rather than a throw: a source asking for its own channels is in the
     * middle of drawing a shelf, and an empty shelf with the source still listed is a better
     * answer than an error screen for a file that may simply be down this minute.
     */
    suspend fun channels(url: String): List<M3uChannel> {
        channelCache[url]?.let { return it }
        return withIOContext {
            runCatching { M3uParser.parse(fetch(url)) }
                .getOrElse {
                    logcat(LogPriority.INFO, it) { "M3U playlist could not be read: $url" }
                    emptyList()
                }
                .also { if (it.isNotEmpty()) channelCache[url] = it }
        }
    }

    /** Drop what is held so the next browse gets today's file. */
    fun invalidate(url: String) {
        channelCache.remove(url)
    }

    private suspend fun fetch(url: String): String =
        network.client.newCall(GET(url)).awaitSuccess().body.string()

    private fun nameOf(text: String, url: String): String {
        val declared = text.lineSequence()
            .firstOrNull { it.trimStart().startsWith(PLAYLIST_DIRECTIVE, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        if (!declared.isNullOrBlank()) return declared
        return runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
    }

    private fun read(): List<M3uPlaylist> = stored.get().mapNotNull {
        runCatching { json.decodeFromString<M3uPlaylist>(it) }.getOrNull()
    }.sortedBy { it.name.lowercase() }

    private fun write(playlists: List<M3uPlaylist>) {
        stored.set(playlists.map { json.encodeToString(it) }.toSet())
        _playlists.value = playlists.sortedBy { it.name.lowercase() }
    }

    companion object {
        private const val PREF_KEY = "animato_m3u_playlists"
        private const val PLAYLIST_DIRECTIVE = "#PLAYLIST"

        /*
         * Failure messages, as sentences rather than as exception class names. The screen shows
         * whatever comes back under the address field, and "IllegalStateException" under a text
         * box is a message about our code rather than about what the person typed.
         */
        const val EMPTY = "Enter a playlist address"
        const val ALREADY_ADDED = "That playlist is already here"
        const val NO_CHANNELS = "No channels in that file — is it an M3U playlist?"
    }
}
