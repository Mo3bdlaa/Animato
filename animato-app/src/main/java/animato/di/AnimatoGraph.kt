package animato.di

import android.app.Application
import animato.anime.di.AnimatoScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.Provides
import mihon.app.di.AppGraph
import tachiyomi.data.Database

/**
 * Animato's dependency graph: the anime half, and everything this fork adds, checked by the compiler.
 *
 * ## Where its bindings come from
 *
 * - **This fork's classes**, marked `@Inject` where they are declared, `@SingleIn(AnimatoScope)` for
 *   the ones there must be one of, `@ContributesBinding(AnimatoScope)` where they are asked for by
 *   interface. Metro finds those itself.
 * - **[AppGraph]**, Mihon's graph, included whole: every type it exposes — its preferences, managers,
 *   caches, network, JSON — is a binding here, and the instance is Mihon's own. Nothing Mihon holds
 *   is built twice for anything it exposes.
 * - **[MihonBindings]**, for the handful of Mihon's types the anime side needs and `AppGraph` does
 *   not expose. Each says what it is and why a second instance is harmless, or — for the database —
 *   where Mihon's own instance comes from.
 * - **[AnimeBindings]**, for what cannot be marked `@Inject` where it is declared: the anime
 *   database, which is SQLDelight-generated, and the preference whose value depends on the build.
 *
 * Mihon's own classes that are `@Inject` and unscoped — its interactors — are built here directly,
 * from the factories Metro generated in Mihon's modules. A scoped one this graph has no binding
 * for is a compile error rather than a silent second copy: Metro refuses to build an
 * `@SingleIn(AppScope)` class in a graph of another scope.
 *
 * ## Injekt
 *
 * [InjektAccessors] gives the graph one accessor per type any of this fork's code still asks Injekt
 * for, and [AnimatoInjektRegistrar] answers Injekt from them. That is what makes the remaining
 * `Injekt.get()` calls safe: each one is a type this graph has been proven, at compile time, to be
 * able to build.
 */
@DependencyGraph(
    scope = AnimatoScope::class,
    bindingContainers = [AnimeBindings::class, MihonBindings::class],
)
interface AnimatoGraph : InjektAccessors {

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Includes mihon: AppGraph,
            @Provides application: Application,
            @Provides mihonDatabase: Database,
        ): AnimatoGraph
    }
}
