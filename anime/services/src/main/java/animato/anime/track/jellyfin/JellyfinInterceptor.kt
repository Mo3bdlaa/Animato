package animato.anime.track.jellyfin

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import okhttp3.Interceptor
import okhttp3.Response
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.security.MessageDigest

/**
 * Signs Jellyfin requests with the key the Jellyfin *extension* already holds.
 *
 * There is no separate sign-in for this tracker, and that is the whole design: the server, the
 * account and the key were configured once in the extension, and asking for them a second time
 * would be asking the user to type the same thing twice. So the key is read out of the extension's
 * own preferences at request time.
 *
 * Finding which extension is the awkward part. An extension's source id is a hash of its name,
 * language and version, so the ids of the ten Jellyfin sources a user may have configured are
 * derived here the same way the extension derives its own — and the right one is the one whose
 * stored user id matches the request's.
 */
class JellyfinInterceptor : Interceptor {

    private val sourceManager: AnimeSourceManager by injectLazy()

    private val apiKeys = mutableMapOf<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Already signed — the caller built a url with a key in it.
        if (request.url.queryParameter("api_key") != null) {
            return chain.proceed(request)
        }

        val userId = request.url.queryParameter("userId") ?: request.url.pathSegments[1]
        val apiKey = apiKeys[userId]
            ?: apiKeyFor(userId)?.also { apiKeys[userId] = it }
            ?: throw IOException("Sign in through the Jellyfin extension first")

        val signed = request.url.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .build()

        return chain.proceed(request.newBuilder().url(signed).build())
    }

    private fun apiKeyFor(userId: String): String? {
        for (index in 1..MAX_JELLYFIN_SOURCES) {
            val source = sourceManager.get(sourceId(index)) as? ConfigurableAnimeSource ?: continue
            val preferences = source.sourcePreferences()

            val configuredUser = preferences.getString("user_id", "")
            if (configuredUser.isNullOrEmpty()) continue
            if (configuredUser == userId) return preferences.getString("api_key", "")
        }
        return null
    }

    /**
     * The id the Jellyfin extension gives its [index]th configured server, derived as it derives
     * it: the first is unsuffixed and the rest are numbered.
     */
    private fun sourceId(index: Int): Long {
        val key = "jellyfin" + (if (index == 1) "" else " ($index)") + "/all/$JELLYFIN_VERSION_ID"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7)
            .map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    private companion object {
        const val JELLYFIN_VERSION_ID = 1
        const val MAX_JELLYFIN_SOURCES = 10
    }
}
