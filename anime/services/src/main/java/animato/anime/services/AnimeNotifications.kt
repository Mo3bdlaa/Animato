package animato.anime.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.Constants
import eu.kanade.tachiyomi.ui.main.MainActivity

/**
 * Notification identifiers, channels and intents for the anime side.
 *
 * Aniyomi put all of this inside Mihon's own `Notifications` and `NotificationHandler`, which is
 * exactly the kind of edit that made that codebase impossible to update. Everything anime-only
 * lives here instead; the ids and channel names are unchanged, so an existing install keeps its
 * notification channels and its user-facing settings for them.
 */
object AnimeNotifications {
    const val CHANNEL_DOWNLOADER_EPISODE_PROGRESS = "downloader_episode_progress_channel"
    const val ID_DOWNLOAD_EPISODE_PROGRESS = -203
    const val ID_DOWNLOAD_EPISODE_ERROR = -204

    const val CHANNEL_NEW_CHAPTERS_EPISODES = "new_chapters_episodes_channel"
    const val ID_NEW_EPISODES = -1301
    const val GROUP_NEW_EPISODES = "eu.kanade.tachiyomi.NEW_EPISODES"

    const val CHANNEL_TORRENT_SERVER = "torrent_server_channel"
    const val ID_TORRENT_SERVER = -801

    /** Opens the anime download queue. */
    fun openAnimeDownloadManagerPendingActivity(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            action = Constants.SHORTCUT_ANIME_DOWNLOADS
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
