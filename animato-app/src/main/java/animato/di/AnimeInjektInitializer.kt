package animato.di

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import animato.anime.net.AnimatoProxySelector
import animato.anime.services.AnimeNotifications

/**
 * Runs the anime side's own application start-up at the first moment it can take effect.
 *
 * A content provider is used purely as a hook, not as a provider: it is the only component Android
 * creates during application bind, which is what makes the timing below work. It answers no queries.
 *
 * ## Why a post, and why it goes to the front
 *
 * `ActivityThread.handleBindApplication` runs on the main thread as a single message, and does two
 * things in order: it creates content providers, then it calls `Application.onCreate`. Our
 * [onCreate] therefore runs *before* Mihon's — too early, because `App.onCreate` starts by calling
 * `patchInjekt()`, which replaces the global Injekt scope and would throw our registration away.
 *
 * Posting to the main looper resolves the first half of that: the looper cannot begin the posted
 * message until the one it is running has returned, and `Application.onCreate` happens inside that
 * one. So the runnable is guaranteed to execute after `patchInjekt()`.
 *
 * The second half is what the first version of this got wrong. It assumed the launch of whatever
 * started the process would be queued *after* our post, because it is dispatched after bind
 * finishes. That is not how it arrives: the activity manager sends `bindApplication` and the
 * launch transaction one after the other over binder, so the launch is often already sitting in
 * the queue while the provider is still being created. An ordinary post lands behind it, the
 * activity runs first, and the first `Injekt.get` for an anime type dies on the spot — reported
 * from a device as a crash on launch, in `MainActivity.onCreate`, before a frame was drawn.
 *
 * `postAtFrontOfQueue` fixes exactly that and nothing else. It cannot jump the message that is
 * running, so it is still after `patchInjekt()`; it does jump the ones already waiting, which is
 * every entry point the process could have been started for.
 *
 * [AnimeInjekt] re-registers if the scope was replaced anyway, so the ordering above decides when
 * this happens, never whether it is correct — and [animato.app.MainActivity] asks for it again
 * before it touches anything, which costs one reference comparison and would have made the crash
 * above impossible on its own.
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
        /*
         * Before the post, not inside it, and this is the one thing here that must be.
         *
         * OkHttp captures the default proxy selector when a client is *built*, so ours has to be
         * the default before the first client exists. `App.onCreate` can build one, and it runs
         * between this method returning and the posted message — so a client built there would
         * capture the previous default and keep it for the life of the process.
         *
         * Safe this early because it reads nothing: see AnimatoProxySelector.install.
         */
        AnimatoProxySelector.install()
        Handler(Looper.getMainLooper()).postAtFrontOfQueue {
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
