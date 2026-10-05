package animato.anime.services

/**
 * Lets a background worker make sure Animato's Injekt registry is in place before it asks for
 * anything.
 *
 * The registry is installed on the main thread, in a message posted to run right after Mihon's
 * `App.onCreate` (see `animato.di.AnimeInjektInitializer`). Everything that starts on the main
 * thread runs after that message. WorkManager does not: it builds workers on its own threads, and
 * can build one while `App.onCreate` is still running — after Mihon has put its own, read-only
 * Injekt in place and before ours replaces it. A worker built in that window asks Mihon's shim for a
 * type it does not have and is thrown away, which is how the scheduled backup failed on its first
 * run after the update to Metro.
 *
 * The installer itself lives in `:animato-app`, which this module cannot see, so the app hands it
 * over here as early as it can, and every worker calls [ensure] before its first `Injekt.get()`.
 * Installing is idempotent and cheap when already done.
 */
object AnimeInjektHook {

    @Volatile
    var install: (() -> Unit)? = null

    fun ensure() {
        install?.invoke()
    }
}
