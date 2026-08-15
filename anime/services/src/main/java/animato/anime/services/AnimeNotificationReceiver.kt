package animato.anime.services

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.SetSeenStatus
import uy.kohesive.injekt.injectLazy

/**
 * Handles the notification actions belonging to the anime side.
 *
 * Aniyomi added these to Mihon's own `NotificationReceiver`, which no amount of copying can undo:
 * a receiver is declared in the manifest and dispatches on intent actions, so ours has to be a
 * receiver of its own. Mihon's keeps handling Mihon's actions and never learns this one exists.
 *
 * The action strings and extras are byte-identical to Aniyomi's, so notifications posted by an
 * older build still resolve.
 */
class AnimeNotificationReceiver : BroadcastReceiver() {

    private val downloadManager: AnimeDownloadManager by injectLazy()
    private val getAnime: GetAnime by injectLazy()
    private val getEpisode: GetEpisode by injectLazy()
    private val setSeenStatus: SetSeenStatus by injectLazy()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RESUME_ANIME_DOWNLOADS -> downloadManager.startDownloads()
            ACTION_PAUSE_ANIME_DOWNLOADS -> downloadManager.pauseDownloads()
            ACTION_CLEAR_ANIME_DOWNLOADS -> downloadManager.clearQueue()
            ACTION_CANCEL_ANIMELIB_UPDATE -> AnimeLibraryUpdateJob.stop(context)

            ACTION_MARK_AS_SEEN -> {
                dismiss(context, intent)
                val urls = intent.getStringArrayExtra(EXTRA_EPISODE_URL) ?: return
                val animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1)
                if (animeId > -1) markAsSeen(urls, animeId)
            }

            ACTION_DOWNLOAD_EPISODE -> {
                dismiss(context, intent)
                val urls = intent.getStringArrayExtra(EXTRA_EPISODE_URL) ?: return
                val animeId = intent.getLongExtra(EXTRA_ANIME_ID, -1)
                if (animeId > -1) downloadEpisodes(urls, animeId)
            }
        }
    }

    private fun dismiss(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId > -1) {
            dismissNotification(context, notificationId, intent.getIntExtra(EXTRA_GROUP_ID, 0))
        }
    }

    private fun markAsSeen(episodeUrls: Array<String>, animeId: Long) {
        launchIO {
            val episodes = episodeUrls.mapNotNull { getEpisode.await(it, animeId) }.toTypedArray()
            setSeenStatus.await(true, *episodes)
        }
    }

    private fun downloadEpisodes(episodeUrls: Array<String>, animeId: Long) {
        launchIO {
            val anime = getAnime.await(animeId) ?: return@launchIO
            val episodes = episodeUrls.mapNotNull { getEpisode.await(it, animeId) }
            downloadManager.downloadEpisodes(anime, episodes)
        }
    }

    companion object {
        private const val ID = "eu.kanade.tachiyomi"
        private const val NAME = "NotificationReceiver"

        private const val ACTION_CANCEL_ANIMELIB_UPDATE = "$ID.$NAME.CANCEL_ANIMELIB_UPDATE"
        private const val ACTION_DOWNLOAD_EPISODE = "$ID.$NAME.ACTION_DOWNLOAD_EPISODE"
        private const val ACTION_MARK_AS_SEEN = "$ID.$NAME.ACTION_MARK_AS_SEEN"
        private const val ACTION_RESUME_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_RESUME_ANIME_DOWNLOADS"
        private const val ACTION_PAUSE_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_PAUSE_ANIME_DOWNLOADS"
        private const val ACTION_CLEAR_ANIME_DOWNLOADS = "$ID.$NAME.ACTION_CLEAR_ANIME_DOWNLOADS"

        // Aniyomi reused Mihon's manga extra keys for anime ids and urls. Renaming them would
        // orphan notifications already posted by an older build, so the names stay.
        private const val EXTRA_ANIME_ID = "$ID.$NAME.EXTRA_MANGA_ID"
        private const val EXTRA_EPISODE_URL = "$ID.$NAME.EXTRA_CHAPTER_URL"
        private const val EXTRA_NOTIFICATION_ID = "$ID.$NAME.NOTIFICATION_ID"
        private const val EXTRA_GROUP_ID = "$ID.$NAME.EXTRA_GROUP_ID"

        fun dismissNotification(context: Context, notificationId: Int, groupId: Int? = null) {
            AnimeNotifications.dismiss(context, notificationId, groupId)
        }

        private fun broadcast(context: Context, requestCode: Int, action: String, extras: Intent.() -> Unit = {}) =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, AnimeNotificationReceiver::class.java).apply {
                    this.action = action
                    extras()
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun resumeAnimeDownloadsPendingBroadcast(context: Context): PendingIntent =
            broadcast(context, 0, ACTION_RESUME_ANIME_DOWNLOADS)

        fun pauseAnimeDownloadsPendingBroadcast(context: Context): PendingIntent =
            broadcast(context, 0, ACTION_PAUSE_ANIME_DOWNLOADS)

        fun clearAnimeDownloadsPendingBroadcast(context: Context): PendingIntent =
            broadcast(context, 0, ACTION_CLEAR_ANIME_DOWNLOADS)

        fun cancelAnimelibUpdatePendingBroadcast(context: Context): PendingIntent =
            broadcast(context, 0, ACTION_CANCEL_ANIMELIB_UPDATE)

        fun markAsViewedPendingBroadcast(
            context: Context,
            animeId: Long,
            episodeUrls: Array<String>,
            groupId: Int,
        ): PendingIntent = broadcast(context, animeId.hashCode(), ACTION_MARK_AS_SEEN) {
            putExtra(EXTRA_EPISODE_URL, episodeUrls)
            putExtra(EXTRA_ANIME_ID, animeId)
            putExtra(EXTRA_NOTIFICATION_ID, animeId.hashCode())
            putExtra(EXTRA_GROUP_ID, groupId)
        }

        fun downloadEpisodesPendingBroadcast(
            context: Context,
            animeId: Long,
            episodeUrls: Array<String>,
            groupId: Int,
        ): PendingIntent = broadcast(context, animeId.hashCode(), ACTION_DOWNLOAD_EPISODE) {
            putExtra(EXTRA_EPISODE_URL, episodeUrls)
            putExtra(EXTRA_ANIME_ID, animeId)
            putExtra(EXTRA_NOTIFICATION_ID, animeId.hashCode())
            putExtra(EXTRA_GROUP_ID, groupId)
        }

        /**
         * Opens an anime's details.
         *
         * Routed through the main activity rather than started directly, so that the screen this
         * lands on is decided by our navigation rather than by a hard reference from a service.
         */
        fun openAnimeEntryPendingActivity(context: Context, animeId: Long): PendingIntent =
            AnimeNotifications.openMainActivity(
                context,
                AnimeConstants.SHORTCUT_ANIME,
                requestCode = animeId.hashCode(),
            ) {
                putExtra(Constants.MANGA_EXTRA, animeId)
                putExtra("notificationId", animeId.hashCode())
            }

        /**
         * Opens an episode in the player.
         *
         * The player is not part of this module — it sits above it — so this hands the ids to the
         * main activity and lets navigation open the player.
         */
        fun openEpisodePendingActivity(context: Context, animeId: Long, episodeId: Long): PendingIntent =
            AnimeNotifications.openMainActivity(
                context,
                AnimeConstants.SHORTCUT_ANIME,
                requestCode = animeId.hashCode(),
            ) {
                putExtra(Constants.MANGA_EXTRA, animeId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
                putExtra("notificationId", animeId.hashCode())
            }

        const val EXTRA_EPISODE_ID = "$ID.$NAME.EXTRA_EPISODE_ID"
    }
}
