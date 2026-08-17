package animato.app.coil

import android.content.Context
import coil3.ComponentRegistry
import coil3.SingletonImageLoader
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.map.Mapper
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.reflect.KClass

/**
 * Adds the anime half to the image loader Mihon built.
 *
 * ## Why it is done this way and not in an `Application`
 *
 * Coil's singleton loader is created once, by whichever `Application` implements
 * `SingletonImageLoader.Factory`. That application is Mihon's `App` — this fork does not declare one
 * of its own, because `App` does a great deal on startup and is `final`, so it can be neither
 * replaced nor extended. Its `newImageLoader` registers the manga cover fetcher and keyers and
 * nothing else, and it is not going to grow anime components: Mihon has no anime.
 *
 * So the loader is taken as built and extended. `newBuilder` keeps every decision Mihon made — the
 * memory cache size, the crossfade, RGB565 on low-memory devices, the image decoder, the okhttp
 * factory — and adds the components without which an [AnimeCover] is a type Coil has never heard
 * of. Reimplementing the builder here instead would mean quietly owning Mihon's image configuration
 * and drifting from it on every sync.
 *
 * ## When
 *
 * From `MainActivity.onCreate`, before anything is drawn. The loader is built on first use rather
 * than at process start, so replacing it here happens before any screen has asked for an image —
 * and [install] is idempotent, so a recreated activity does not rebuild it.
 */
object AnimatoImageLoader {

    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true

        val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
        val current = SingletonImageLoader.get(context)

        /*
         * This copy is selective, and both of its failure modes have been seen on a device.
         *
         * Version one replaced the registry — `Builder.components { }` builds a fresh registry
         * rather than adding — and manga covers vanished, because Mihon's fetchers went with it.
         *
         * Version two copied `current.components` wholesale. That field is not "what Mihon
         * registered": `RealImageLoader` composes it at construction as user components + platform
         * components + its own terminal `EngineInterceptor`, appended last. Copying it therefore
         * smuggled the OLD loader's engine into the new loader's interceptor chain, ahead of the
         * new loader's own engine. An engine interceptor never calls `proceed`, so every request
         * was served by the old engine against the old registry — which knows manga and has never
         * heard of anime. Manga covers worked, anime covers broke, and the four anime components
         * sat unreachable behind a terminated chain.
         *
         * So: copy every *loadable* — mappers, keyers, fetchers, decoders, which are inert
         * registrations — and no interceptors. The only interceptor in the composed registry is
         * the old engine (Mihon's `newImageLoader` registers none of its own), and the new loader
         * appends its own engine and platform components when it is built.
         */
        val extended = current.newBuilder()
            .components(
                ComponentRegistry.Builder()
                    .addAllLoadables(current.components)
                    .add(AnimeCoverFetcher.AnimeCoverFactory(callFactoryLazy))
                    .add(AnimeCoverFetcher.AnimeFactory(callFactoryLazy))
                    .add(AnimeCoverKeyer())
                    .add(AnimeKeyer())
                    .build(),
            )
            .build()

        SingletonImageLoader.setUnsafe(extended)
    }
}

/**
 * Copies every component that describes *how to load something* — and none that carry a reference
 * back to the loader they came from. Interceptors are excluded deliberately: see the comment at the
 * call site in [AnimatoImageLoader.install].
 */
@Suppress("UNCHECKED_CAST")
internal fun ComponentRegistry.Builder.addAllLoadables(from: ComponentRegistry): ComponentRegistry.Builder = apply {
    from.mappers.forEach { (mapper, type) -> add(mapper as Mapper<Any, Any>, type as KClass<Any>) }
    from.keyers.forEach { (keyer, type) -> add(keyer as Keyer<Any>, type as KClass<Any>) }
    from.fetcherFactories.forEach { (factory, type) -> add(factory as Fetcher.Factory<Any>, type as KClass<Any>) }
    from.decoderFactories.forEach { add(it) }
}
