package animato.di

import android.app.Application
import dev.zacsweers.metro.createGraphFactory
import mihon.app.di.AppGraph
import mihon.app.di.injekt.MetroInjektRegistrar
import mihon.core.metro.GraphProvider
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.lang.reflect.Type

/**
 * The Injekt registry Animato runs on, answered from [AnimatoGraph].
 *
 * Nothing is registered in Injekt any more; every binding is in the Metro graph. This exists
 * because code still *asks* Injekt — `Injekt.get()` defaults, `by injectLazy()` fields — and
 * because extensions do. A request is answered, in order, by:
 *
 * 1. Mihon's own read-only Injekt shim, for the handful of types extensions ask for, so they get
 *    exactly the instances Mihon gives its manga extensions.
 * 2. [AnimatoGraph], through [InjektAccessors] — one accessor per type any of this fork's code asks
 *    for, each proven buildable by the compiler.
 *
 * A type in neither is an InjektionException, as it would be anywhere else. It cannot be one of
 * ours: the accessor list is generated from the call sites, and CI fails while it is stale.
 *
 * The graph is created on first use, not here. Mihon builds its own in `App.onCreate`, after a step
 * that must come first, and Animato's is built from Mihon's.
 */
internal class AnimatoInjektRegistrar(application: Application) : DefaultRegistrar() {

    @Suppress("UNCHECKED_CAST")
    private val graphProvider = application as GraphProvider<AppGraph>

    private val mihon = MetroInjektRegistrar(application, graphProvider)

    val graph: AnimatoGraph by lazy {
        val mihonGraph = graphProvider.graph
        createGraphFactory<AnimatoGraph.Factory>().create(
            mihon = mihonGraph,
            application = application,
            mihonDatabase = MihonDatabase.from(mihonGraph),
        )
    }

    private val bindings by lazy { graph.injektBindings() }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? =
        super.getInstanceOrNull(forType)
            ?: mihon.getInstanceOrNull(forType)
            ?: bindings[forType]?.invoke() as R?

    override fun <R : Any> getInstance(forType: Type): R =
        getInstanceOrNull(forType) ?: throw InjektionException("No instance of $forType in Animato's or Mihon's graph")

    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstanceOrNull(forType) ?: default

    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R =
        getInstanceOrNull(forType) ?: default()
}
