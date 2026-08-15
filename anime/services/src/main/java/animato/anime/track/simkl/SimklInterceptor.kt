package animato.anime.track.simkl

import animato.anime.track.simkl.SimklApi.Companion.CLIENT_ID
import animato.anime.track.simkl.dto.SimklOAuth
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Signs Simkl requests, and holds the token that signs them.
 *
 * The token lives here rather than being read from preferences per request because Simkl's does
 * not expire and has no refresh: there is nothing to renew, only something to remember.
 */
class SimklInterceptor(private val simkl: AnimeSimkl) : Interceptor {

    private var oauth: SimklOAuth? = simkl.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = oauth ?: throw IllegalStateException("Not signed in to Simkl")

        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${token.accessToken}")
            .addHeader("simkl-api-key", CLIENT_ID)
            .build()

        return chain.proceed(request)
    }

    fun newAuth(oauth: SimklOAuth?) {
        this.oauth = oauth
        simkl.saveToken(oauth)
    }
}
