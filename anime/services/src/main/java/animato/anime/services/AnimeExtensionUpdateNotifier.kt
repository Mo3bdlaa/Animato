package animato.anime.services

import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Tells the user that anime extensions have updates waiting.
 *
 * Aniyomi did this by adding an `anime: Boolean = false` parameter to Mihon's own
 * [eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier], switching only which screen the
 * notification opened. That is an edit to a file upstream owns, so this is a notifier of ours
 * instead — and one flag fewer, since a class that does one thing does not need to be told which
 * thing it is doing.
 *
 * It keeps Mihon's channel, so the two kinds of extension update stay under one user-facing
 * setting, but takes an id of its own: sharing [Notifications.ID_UPDATES_TO_EXTS] meant an anime
 * notification replaced a pending manga one, and the user lost the manga list.
 */
class AnimeExtensionUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {

    fun promptUpdates(names: List<String>) {
        context.notify(
            ID_UPDATES_TO_ANIME_EXTS,
            Notifications.CHANNEL_EXTENSIONS_UPDATE,
        ) {
            setContentTitle(
                context.pluralStringResource(
                    MR.plurals.update_check_notification_ext_updates,
                    names.size,
                    names.size,
                ),
            )
            if (!securityPreferences.hideNotificationContent.get()) {
                val extNames = names.joinToString(", ")
                setContentText(extNames)
                setStyle(NotificationCompat.BigTextStyle().bigText(extNames))
            }
            setSmallIcon(R.drawable.ic_extension_24dp)
            setContentIntent(
                AnimeNotifications.openMainActivity(
                    context,
                    AnimeConstants.SHORTCUT_ANIMEEXTENSIONS,
                    requestCode = 0,
                ),
            )
            setAutoCancel(true)
        }
    }

    fun dismiss() {
        context.cancelNotification(ID_UPDATES_TO_ANIME_EXTS)
    }

    companion object {
        /** Mihon uses -401 for manga extension updates and -402 for its installer; this follows them. */
        const val ID_UPDATES_TO_ANIME_EXTS = -403
    }
}
