package animato.di

import android.app.Application
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope

/**
 * Registers the anime modules with Injekt.
 *
 * Mihon's `App` is final and its `onCreate` has no extension point, so we cannot add a line to it
 * the way Aniyomi did — and we would not want to. That leaves one constraint to respect and one
 * hazard to defend against.
 *
 * The constraint: `App.onCreate` begins with `patchInjekt()`, which does not merge into the
 * existing scope but replaces the global one outright. Anything registered before that call is
 * silently discarded. [AnimeInjektInitializer] is what arranges for this to run afterwards.
 *
 * The hazard: if that ever fails to hold — a future upstream change, an entry point we did not
 * anticipate — the failure would be an `Injekt.get()` throwing deep inside a background service.
 * So this does not merely remember *that* it registered; it remembers *which scope instance* it
 * registered into. If the global scope has since been replaced, the registration is redone. That
 * makes an early call harmless rather than fatal, and lets any entry point call this defensively
 * for the cost of one reference comparison.
 */
object AnimeInjekt {

    private var registeredInto: InjektScope? = null

    @Synchronized
    fun ensureRegistered(app: Application) {
        val scope = Injekt
        if (registeredInto === scope) return

        scope.importModule(AnimePreferenceModule(app))
        scope.importModule(AnimeAppModule(app))
        scope.importModule(AnimeDomainModule())
        scope.importModule(AnimePlayerModule(app))

        registeredInto = scope
    }
}
