package animato.di

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import animato.anime.services.AnimeNotifications

/**
 * Runs the anime side's own application start-up at the first moment it can take effect.
 *
 * A content provider is used purely as a hook, not as a provider: it is the only component Android
 * creates during application bind, which is what makes the timing below work. It answers no queries.
 *
 * ## Why a post, and why this is not a race
 *
 * `ActivityThread.handleBindApplication` runs on the main thread as a single message, and does two
 * things in order: it creates content providers, then it calls `Application.onCreate`. Our
 * [onCreate] therefore runs *before* Mihon's — too early, because `App.onCreate` starts by calling
 * `patchInjekt()`, which replaces the global Injekt scope and would throw our registration away.
 *
 * Posting to the main looper resolves that. The looper cannot begin the posted message until the
 * message it is currently running has returned, and `Application.onCreate` happens inside that one.
 * So the runnable is guaranteed to execute after `patchInjekt()` — and, being queued during bind,
 * ahead of the activity, service or broadcast dispatch that triggered the process start, since
 * those are enqueued after bind completes.
 *
 * [AnimeInjekt] re-registers if the scope was replaced anyway, so the guarantee above decides when
 * this happens, never whether it is correct.
 *
 * ## What else rides here
 *
 * The anime notification channels. Mihon creates its own in `App.onCreate` and cannot know about
 * ours, and nothing may be posted to a channel that does not exist — the torrent server and the
 * HTTP server both call `startForeground`, which throws outright when the channel is missing. The
 * same message that guarantees the registration lands after `patchInjekt()` guarantees the channels
 * exist before any service the process was started for can run.
 */
class AnimeInjektInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? android.app.Application ?: return false
        Handler(Looper.getMainLooper()).post {
            AnimeInjekt.ensureRegistered(app)
            AnimeNotifications.createChannels(app)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
