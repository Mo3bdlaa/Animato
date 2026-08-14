package animato.anime.services

/**
 * Notification identifiers and channels for the anime side.
 *
 * Aniyomi put these inside Mihon's own `Notifications` object, which is exactly the kind of edit
 * that made that codebase impossible to update. They live here instead; the numbers are unchanged
 * so an existing install keeps its notification channels.
 */
object AnimeNotifications {
    const val ID_DOWNLOAD_EPISODE_PROGRESS = -203
    const val ID_DOWNLOAD_EPISODE_ERROR = -204

    const val CHANNEL_NEW_CHAPTERS_EPISODES = "new_chapters_episodes_channel"
    const val ID_NEW_EPISODES = -1301
    const val GROUP_NEW_EPISODES = "eu.kanade.tachiyomi.NEW_EPISODES"
}
