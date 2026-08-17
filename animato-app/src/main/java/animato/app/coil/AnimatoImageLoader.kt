package animato.app.coil

import android.content.Context
import coil3.SingletonImageLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
 * factory — and adds the two components without which an [AnimeCover] is a type Coil has never heard
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
         * Built from the existing registry, not from an empty one.
         *
         * `Builder.components { }` does not add to what is there — it constructs a fresh
         * `ComponentRegistry` and assigns it, discarding whatever the loader already had. So the
         * first version of this replaced Mihon's manga cover fetcher and keyers with the two anime
         * ones, and the result was exactly as reported from a device: *"anime covers appeared but
         * manga covers vanished."* One half fixed by breaking the other, which is worse than the
         * bug it was fixing, because manga covers had always worked.
         *
         * Starting from `current.components` and adding to it cannot lose anything.
         */
        val extended = current.newBuilder()
            .components(
                current.components.newBuilder()
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
