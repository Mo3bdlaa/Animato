package animato.di

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope

/**
 * Installs Animato's Injekt registry and registers the anime modules in it.
 *
 * Mihon's `App` is final and its `onCreate` has no extension point, so we cannot add a line to it
 * the way Aniyomi did — and we would not want to. That leaves one constraint to respect and one
 * hazard to defend against.
 *
 * The constraint: `App.onCreate` sets the global Injekt scope to Mihon's own, which is read-only
 * and replaces whatever was there. Anything installed before that is silently discarded.
 * [AnimeInjektInitializer] is what arranges for this to run afterwards. What is installed is not
 * Mihon's scope with modules added — Mihon's refuses additions — but [AnimatoInjektRegistrar],
 * which holds the anime modules and answers for Mihon's types out of Mihon's graph.
 *
 * The hazard: if that ever fails to hold — a future upstream change, an entry point we did not
 * anticipate — the failure would be an `Injekt.get()` throwing deep inside a background service.
 * So this does not merely remember *that* it installed; it remembers *which scope instance* it
 * installed. If the global scope has since been replaced, the installation is redone. That makes
 * an early call harmless rather than fatal, and lets any entry point call this defensively for the
 * cost of one reference comparison.
 */
object AnimeInjekt {

    private var installed: InjektScope? = null

    @Synchronized
    fun ensureRegistered(app: Application) {
        if (installed != null && installed === Injekt) return

        val scope = InjektScope(AnimatoInjektRegistrar(app))
        scope.importModule(AnimePreferenceModule(app))
        scope.importModule(AnimeAppModule(app))
        scope.importModule(AnimeDomainModule())
        scope.importModule(AnimePlayerModule(app))

        Injekt = scope
        installed = scope
    }
}
