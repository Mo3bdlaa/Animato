package animato.anime.jellyfin

import animato.anime.util.credentialString
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.UUID

/**
 * One server somebody signed in to.
 *
 * The token rather than the password. Jellyfin hands back a long-lived access token in exchange for
 * a sign-in, and keeping that instead means the password is used once and never stored — so a
 * backup of this app's preferences carries something the server can revoke rather than the
 * credential itself.
 *
 * Note there is no private companion object here, deliberately. See [animato.anime.stremio.StremioAddon]
 * for the crash that rule exists because of.
 */
@Serializable
data class JellyfinServer(
    /** The base address, normalised — see [JellyfinUrls.normalizeBase]. */
    val url: String,
    val name: String,
    val userId: String,
    val token: String,
    val serverId: String = "",
    /**
     * The device id this sign-in was made under.
     *
     * Jellyfin lists active sessions by device, so a stable one per server means this app appears
     * as one entry in the server's dashboard rather than a new row every time it starts — and it
     * means the person can revoke exactly this app's access without touching their other clients.
     */
    val deviceId: String = "",
)

/**
 * The Jellyfin and Emby servers the user has signed in to.
 *
 * ## Why signing in is the add
 *
 * Unlike a Stremio addon or an M3U playlist, there is nothing to validate about the address on its
 * own — a server answers nothing at all without a token. So adding one *is* the sign-in: the
 * credentials go in, a token comes back, and a failure here is the same failure the person would
 * see in any other client, said in the same terms.
 */
class JellyfinServerStore(
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {
    // Private, because the record is an access token with a URL attached: a server entry
    // without its token is nothing you could restore, so the whole thing is the secret.
    private val stored = preferenceStore.credentialString(PREF_KEY)

    private val _servers = MutableStateFlow(read())
    val servers: StateFlow<List<JellyfinServer>> = _servers.asStateFlow()

    /**
     * Sign in, and keep the token.
     *
     * The failure messages are sentences about what the person just typed, for the same reason the
     * other two stores' are: an exception class name under a password field is a message about our
     * code rather than about their server.
     */
    suspend fun signIn(rawUrl: String, username: String, password: String): Result<JellyfinServer> = withIOContext {
        val base = JellyfinUrls.normalizeBase(rawUrl)
        if (base.isEmpty() || !base.startsWith("http")) {
            return@withIOContext Result.failure(IllegalArgumentException(INVALID_URL))
        }
        if (username.isBlank()) {
            return@withIOContext Result.failure(IllegalArgumentException(NO_USERNAME))
        }
        if (_servers.value.any { it.url.equals(base, ignoreCase = true) }) {
            return@withIOContext Result.failure(IllegalStateException(ALREADY_ADDED))
        }

        val deviceId = UUID.randomUUID().toString()
        val body = json.encodeToString(AuthRequest(username = username, pw = password))
            .toRequestBody(JSON_MEDIA_TYPE)

        val auth = try {
            val response = network.client
                .newCall(POST(JellyfinUrls.authenticate(base), authHeaders(deviceId), body))
                .awaitSuccess()
            with(json) { response.parseAs<JellyfinAuthResponse>() }
        } catch (e: IOException) {
            logcat(LogPriority.WARN, e) { "Jellyfin server unreachable: $base" }
            return@withIOContext Result.failure(IOException(UNREACHABLE))
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Jellyfin sign-in refused: $base" }
            // Anything that is not a network failure at this point is the server saying no, and
            // the only thing it ever says no to here is the username and password.
            return@withIOContext Result.failure(IllegalStateException(REFUSED))
        }

        if (auth.accessToken.isBlank() || auth.user.id.isBlank()) {
            return@withIOContext Result.failure(IllegalStateException(NOT_A_SERVER))
        }

        val server = JellyfinServer(
            url = base,
            // The person's own name for their server is not in this response, so the account name
            // is the closest thing to one — and it is what distinguishes two sign-ins to the same
            // machine, which is the case a name has to carry.
            name = auth.user.name.takeIf { it.isNotBlank() } ?: hostOf(base),
            userId = auth.user.id,
            token = auth.accessToken,
            serverId = auth.serverId,
            deviceId = deviceId,
        )
        write(_servers.value + server)
        Result.success(server)
    }

    /**
     * Forget a server.
     *
     * The token is dropped and not revoked. Revoking would be a request to a server that may be
     * the reason somebody is removing it — unreachable, moved, or one they no longer have an
     * account on — and a removal that fails because the thing being removed is gone is the worst
     * possible shape for this. The session can be ended from the server's own dashboard, which is
     * where sessions are managed anyway.
     */
    fun remove(url: String) {
        write(_servers.value.filterNot { it.url.equals(url, ignoreCase = true) })
    }

    fun get(url: String): JellyfinServer? = _servers.value.firstOrNull { it.url.equals(url, ignoreCase = true) }

    /**
     * The headers every request to a server carries.
     *
     * The `Authorization` value is Jellyfin's own scheme rather than a bearer token: the server
     * reads the client name and version out of it and shows them in its dashboard, so a sign-in
     * from here is legible as this app rather than as an anonymous session.
     */
    fun headers(server: JellyfinServer): Headers =
        authHeaders(server.deviceId, server.token)

    private fun authHeaders(deviceId: String, token: String? = null): Headers {
        val parts = buildList {
            add("Client=\"$CLIENT_NAME\"")
            add("Device=\"$DEVICE_NAME\"")
            add("DeviceId=\"$deviceId\"")
            add("Version=\"$CLIENT_VERSION\"")
            token?.takeIf { it.isNotBlank() }?.let { add("Token=\"$it\"") }
        }
        return Headers.Builder()
            .add("Authorization", "MediaBrowser " + parts.joinToString(", "))
            .add("Content-Type", "application/json")
            .build()
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

    private fun read(): List<JellyfinServer> {
        val raw = stored.get()
        if (raw.isEmpty()) return emptyList()
        return runCatching { json.decodeFromString<List<JellyfinServer>>(raw) }
            .getOrElse {
                logcat(LogPriority.ERROR, it) { "Stored Jellyfin servers could not be read" }
                emptyList()
            }
    }

    private fun write(servers: List<JellyfinServer>) {
        stored.set(json.encodeToString(servers))
        _servers.value = servers
    }

    @Serializable
    private data class AuthRequest(
        @kotlinx.serialization.SerialName("Username") val username: String,
        @kotlinx.serialization.SerialName("Pw") val pw: String,
    )

    companion object {
        private const val PREF_KEY = "animato_jellyfin_servers"

        private const val CLIENT_NAME = "Animato"
        private const val DEVICE_NAME = "Android"
        private const val CLIENT_VERSION = "1.0"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /*
         * Sentences, not exception names. The dialog shows whatever comes back under the fields.
         */
        const val INVALID_URL = "That does not look like a server address"
        const val NO_USERNAME = "Enter the username you use on that server"
        const val ALREADY_ADDED = "That server is already here"
        const val UNREACHABLE = "Could not reach that server — is it on, and reachable from here?"
        const val REFUSED = "That username and password were not accepted"
        const val NOT_A_SERVER = "That answered, but not like a Jellyfin or Emby server"
    }
}
