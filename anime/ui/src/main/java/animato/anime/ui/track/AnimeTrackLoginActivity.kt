package animato.anime.ui.track

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import animato.anime.track.AnimeTrackerManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.injectLazy

/**
 * Catches the browser coming back from an anime tracker's sign-in page.
 *
 * Mihon has `TrackLoginActivity` for the same job and it cannot be extended: it names each tracker
 * on its own manager, and the base class it shares keeps `returnToSettings` `internal` to `:app`.
 * So this is the anime half of the same idea, answering only for trackers Mihon does not have.
 *
 * It shows a loading screen because the code has to be exchanged for a token before the user is
 * sent back, and that is a request over the network — finishing immediately would leave them
 * looking at the settings screen while the sign-in silently completed behind it.
 */
class AnimeTrackLoginActivity : BaseActivity() {

    private val trackerManager: AnimeTrackerManager by injectLazy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { LoadingScreen() }

        val data = intent.data
        if (data == null) {
            returnToSettings()
            return
        }

        lifecycleScope.launch {
            handle(data)
            returnToSettings()
        }
    }

    private suspend fun handle(uri: Uri) {
        // An OAuth answer arrives either as a query or as a fragment, depending on the flow.
        val parameters = (uri.encodedQuery?.takeIf { it.isNotBlank() } ?: uri.encodedFragment)
            ?.split("&")
            ?.filter { it.isNotBlank() }
            ?.associate {
                val parts = it.split("=", limit = 2).map<String, String>(Uri::decode)
                parts[0] to parts.getOrNull(1)
            }
            .orEmpty()

        when (uri.host) {
            SIMKL_HOST -> simkl(parameters["code"])
        }
    }

    private suspend fun simkl(code: String?) {
        if (code == null) {
            trackerManager.simkl.logout()
            return
        }
        try {
            trackerManager.simkl.login(code)
        } catch (e: Exception) {
            // login() has already signed out; this is only so the failure is not silent.
            logcat(LogPriority.ERROR, e) { "Simkl sign-in failed" }
        }
    }

    private fun returnToSettings() {
        finish()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private companion object {
        /**
         * Aniyomi's host, because Simkl only redirects to the address its client id is registered
         * with. `SimklApi.REDIRECT_URL` has the full reasoning.
         */
        const val SIMKL_HOST = "simkl-auth"
    }
}
