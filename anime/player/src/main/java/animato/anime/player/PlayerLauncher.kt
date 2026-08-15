package animato.anime.player

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.player.service.HttpServerService
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Starts playback, internally or in an external player.
 *
 * Aniyomi kept this in the companion object of its `MainActivity`, which meant every screen that
 * plays an episode reached into the activity to do it. Our `MainActivity` is a different class in a
 * different module, so that was never going to survive the port — and it should not, because none of
 * this is about being an activity. It belongs with the player.
 *
 * The one genuinely activity-shaped part is [externalPlayerResult]: launching an external player and
 * getting the playback position back needs an `ActivityResultLauncher`, which only an activity can
 * register. So the activity registers it here at startup and this stays a plain object.
 */
object PlayerLauncher {

    /**
     * Set by the activity that can receive the external player's result.
     *
     * Null until then, and [startPlayerActivity] returns without launching rather than crashing —
     * external playback is a preference, and an app that has not wired this up yet should decline
     * it, not die.
     */
    var externalPlayerResult: ActivityResultLauncher<Intent>? = null

    suspend fun startPlayerActivity(
        context: Context,
        animeId: Long,
        episodeId: Long,
        extPlayer: Boolean,
        sourceId: Long? = null,
        video: Video? = null,
        hosterIndex: Int = -1,
        videoIndex: Int = -1,
        hosterList: List<Hoster>? = null,
    ) {
        if (extPlayer) {
            val resolvedSourceId = sourceId ?: (Injekt.get<GetAnime>().await(animeId)?.source ?: -1L)
            val (success, port) = startHttpServerService(context, resolvedSourceId)
            if (!success) {
                withUIContext { Injekt.get<Application>().toast(AYMR.strings.http_server_start_failure) }
                return
            }

            val servedVideo = video?.copyHttpServer(port)
            val intent = try {
                ExternalIntents.newIntent(context, animeId, episodeId, servedVideo)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                withUIContext { Injekt.get<Application>().toast(e.message) }
                null
            } ?: return
            externalPlayerResult?.launch(intent) ?: return
        } else {
            context.startActivity(
                PlayerActivity.newIntent(
                    context,
                    animeId,
                    episodeId,
                    hosterList,
                    hosterIndex,
                    videoIndex,
                ),
            )
        }
    }

    /**
     * Brings up the local HTTP server that serves a torrent stream to an external player, and waits
     * for it to be listening. External players are given a URL, not a file, so nothing can be handed
     * over until the server is actually up.
     */
    suspend fun startHttpServerService(
        context: Context,
        sourceId: Long,
        timeout: Duration = 5.seconds,
    ): Pair<Boolean, Int> {
        HttpServerService.resetIsRunning()
        context.startService(
            Intent(context, HttpServerService::class.java)
                .putExtra(HttpServerService.EXTRA_SOURCE_ID, sourceId),
        )

        val ready = withTimeoutOrNull(timeout) {
            HttpServerService.isRunning.first { it }
        }

        return Pair(ready == true, HttpServerService.port)
    }
}
