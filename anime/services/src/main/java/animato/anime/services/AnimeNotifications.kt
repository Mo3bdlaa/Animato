package animato.anime.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    const val CHANNEL_HTTP_SERVER = "http_server_channel"
    const val ID_HTTP_SERVER = -901

    /** Opens the anime download queue. */
    fun openAnimeDownloadManagerPendingActivity(context: Context): PendingIntent =
        openMainActivity(context, AnimeConstants.SHORTCUT_ANIME_DOWNLOADS)

    /** Builds a pending intent that brings the app to the given deep-link action. */
    fun openMainActivity(
        context: Context,
        action: String,
        requestCode: Int = 0,
        extras: Intent.() -> Unit = {},
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            this.action = action
            extras()
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Cancels a posted notification, and its summary when it was the last of its group. */
    fun dismiss(context: Context, notificationId: Int, groupId: Int? = null) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(notificationId)
        if (groupId != null && groupId != 0) {
            val remaining = manager.activeNotifications.filter { it.groupKey.endsWith(groupId.toString()) }
            if (remaining.size == 1) manager.cancel(groupId)
        }
    }
}
