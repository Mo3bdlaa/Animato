package animato.di

import android.app.Application
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInjektRegistrar
import mihon.core.metro.GraphProvider
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.lang.reflect.Type

/**
 * The Injekt registry Animato runs on: its own registrations first, Mihon's after.
 *
 * Mihon's own Injekt instance is read-only — it exists for extensions and refuses every
 * registration — so the anime modules cannot be imported into it the way they used to be. This is
 * an ordinary writable registry for those, which falls back to Mihon's shim (the types extensions
 * ask for) and then to Mihon's Metro graph (see [MihonGraphBridge]) for anything it does not hold
 * itself. Code that calls `Injekt.get()` sees one registry, as it always has.
 */
internal class AnimatoInjektRegistrar(application: Application) : DefaultRegistrar() {

    @Suppress("UNCHECKED_CAST")
    private val graphProvider = application as GraphProvider<AppGraph>

    private val mihon = MetroInjektRegistrar(application, graphProvider)

    private val bridge = MihonGraphBridge(graphProvider) { type -> getInstanceOrNull<Any>(type) }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? =
        super.getInstanceOrNull(forType)
            ?: mihon.getInstanceOrNull(forType)
            ?: bridge.lookup(forType) as R?

    override fun <R : Any> getInstance(forType: Type): R =
        getInstanceOrNull(forType) ?: throw InjektionException("No instance of $forType in Animato's or Mihon's graph")

    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstanceOrNull(forType) ?: default

    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        getInstanceOrNull(forType) ?: default()
}
