package animato.app.coil

import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.Decoder
import coil3.fetch.Fetcher
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.request.Options
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Locks in the one property that has now broken cover loading twice from a device: what gets
 * copied when Mihon's registry is extended with the anime components.
 *
 * `ImageLoader.components` is composed by the loader itself — user components plus platform
 * components plus the loader's own terminal `EngineInterceptor`, appended last. Copying that
 * engine into a new loader parks it at the head of the new chain, where it serves every request
 * against the OLD registry and the anime components behind it never run. So the copy must take
 * every loadable and no interceptors — which is exactly what these tests pin down.
 */
class AddAllLoadablesTest {

    private class RecordingInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain) = chain.proceed()
    }

    private class StringKeyer : Keyer<String> {
        override fun key(data: String, options: Options) = data
    }

    private class StringMapper : Mapper<String, Uri> {
        override fun map(data: String, options: Options) = null
    }

    private class StringFetcherFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? = null
    }

    private class NoopDecoderFactory : Decoder.Factory {
        override fun create(
            result: coil3.fetch.SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder? = null
    }

    private fun composedRegistry(): ComponentRegistry {
        // Shaped like the registry a live loader exposes: loadables plus an interceptor the way
        // RealImageLoader appends its engine.
        return ComponentRegistry.Builder()
            .add(StringMapper())
            .add(StringKeyer())
            .add(StringFetcherFactory())
            .add(NoopDecoderFactory())
            .add(RecordingInterceptor())
            .build()
    }

    @Test
    fun `copies every loadable`() {
        val source = composedRegistry()

        val copy = ComponentRegistry.Builder().addAllLoadables(source).build()

        copy.mappers shouldBe source.mappers
        copy.keyers shouldBe source.keyers
        copy.fetcherFactories shouldBe source.fetcherFactories
        copy.decoderFactories shouldBe source.decoderFactories
    }

    @Test
    fun `copies no interceptors`() {
        val source = composedRegistry()

        val copy = ComponentRegistry.Builder().addAllLoadables(source).build()

        // An interceptor copied out of a live loader is that loader's engine.
        copy.interceptors shouldBe emptyList()
    }

    @Test
    fun `components added after the copy are still typed`() {
        val source = composedRegistry()

        val copy = ComponentRegistry.Builder()
            .addAllLoadables(source)
            .add(StringKeyer())
            .build()

        // The pairing of component to KClass is what routes an AnimeCover to the anime fetcher —
        // a copy that lost it would compile and then match nothing at runtime.
        copy.keyers.last().second shouldBe String::class
        copy.fetcherFactories.all { it.second == String::class }.shouldBeTrue()
    }
}
