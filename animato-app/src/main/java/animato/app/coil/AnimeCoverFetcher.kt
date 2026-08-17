package animato.app.coil

import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.getOrDefault
import coil3.request.Options
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.network.await
import logcat.LogPriority
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException

/**
 * Loads an anime's cover, which nothing in the app could do.
 *
 * ## The absence
 *
 * From a device, on a search that had found things: *"the thumbnails don't show at all — I don't
 * know whether it's loading or what."* Every anime cover in the app was a broken-image placeholder:
 * search, Discover, the library grid, the title page.
 *
 * The reason is that Coil cannot load a type nobody taught it about. Covers are not passed as URLs —
 * they are passed as [AnimeCover], a domain object — and turning one into bytes takes a `Keyer` to
 * name it and a `Fetcher` to get it. Mihon's `App` is this application's `Application` class, and
 * its image loader registers exactly three components: the manga cover fetcher, the manga cover
 * keyer and the manga keyer. Mihon has no anime, so it registers nothing for anime, and Aniyomi's
 * equivalents never came across with the rest of the port.
 *
 * So manga covers worked, anime covers did not, and the difference was invisible in every build.
 *
 * ## Why it is a copy rather than a reuse
 *
 * `MangaCoverFetcher` is the same algorithm over the manga types, and the two type families are
 * parallel and unrelated by design — `Anime` is not a `Manga` and neither is an interface. Making
 * one generic implementation would mean editing Mihon's file, which this project does not do. What
 * *is* reused is the one thing worth sharing: [MangaCoverFetcher.USE_CUSTOM_COVER_KEY], so a request
 * that asks to ignore a custom cover means the same thing on both halves.
 *
 * ## What it does, in order
 *
 * A custom cover the user set wins over everything. Then, for a library entry only, the cover cache
 * — that is what makes a shelf readable offline. Then Coil's own disk cache, then the network, and a
 * network hit for something now in the library is moved into the cover cache on the way past.
 *
 * The request goes through **the source's own client and headers**. That is not an optimisation: a
 * great many sources serve covers only to a request carrying their referer, so a plain fetch of the
 * same URL returns 403 and draws the same broken image as no fetcher at all.
 */
class AnimeCoverFetcher(
    private val url: String?,
    private val isLibraryAnime: Boolean,
    private val options: Options,
    private val coverFileLazy: Lazy<File?>,
    private val customCoverFileLazy: Lazy<File>,
    private val diskCacheKeyLazy: Lazy<String>,
    private val sourceLazy: Lazy<AnimeHttpSource?>,
    private val callFactoryLazy: Lazy<Call.Factory>,
    private val imageLoader: ImageLoader,
) : Fetcher {

    private val diskCacheKey: String
        get() = diskCacheKeyLazy.value

    override suspend fun fetch(): FetchResult {
        val useCustomCover = options.extras.getOrDefault(MangaCoverFetcher.USE_CUSTOM_COVER_KEY)
        if (useCustomCover) {
            val customCoverFile = customCoverFileLazy.value
            if (customCoverFile.exists()) {
                return fileLoader(customCoverFile)
            }
        }

        if (url == null) error("No cover specified")
        return when (getResourceType(url)) {
            Type.File -> fileLoader(File(url.substringAfter("file://")))
            Type.URI -> fileUriLoader(url)
            Type.URL -> httpLoader()
            null -> error("Invalid image")
        }
    }

    private fun fileLoader(file: File): FetchResult {
        return SourceFetchResult(
            source = ImageSource(
                file = file.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
                diskCacheKey = diskCacheKey,
            ),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private fun fileUriLoader(uri: String): FetchResult {
        val source = UniFile.fromUri(options.context, uri.toUri())!!
            .openInputStream()
            .source()
            .buffer()
        return SourceFetchResult(
            source = ImageSource(source = source, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/*",
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun httpLoader(): FetchResult {
        // Only the library gets its own cache. Everything else is a cover somebody scrolled past
        // once, and keeping those forever is how a downloads folder becomes the largest thing on
        // the phone.
        val libraryCoverCacheFile = if (isLibraryAnime) {
            coverFileLazy.value ?: error("No cover specified")
        } else {
            null
        }
        if (libraryCoverCacheFile?.exists() == true && options.diskCachePolicy.readEnabled) {
            return fileLoader(libraryCoverCacheFile)
        }

        var snapshot = readFromDiskCache()
        try {
            if (snapshot != null) {
                // Adding something to the library promotes its cover out of the throwaway cache
                // rather than fetching it again.
                val snapshotCoverCache = moveSnapshotToCoverCache(snapshot, libraryCoverCacheFile)
                if (snapshotCoverCache != null) {
                    return fileLoader(snapshotCoverCache)
                }

                return SourceFetchResult(
                    source = snapshot.toImageSource(),
                    mimeType = "image/*",
                    dataSource = DataSource.DISK,
                )
            }

            val response = executeNetworkRequest()
            val responseBody = checkNotNull(response.body) { "Null response source" }
            try {
                val responseCoverCache = writeResponseToCoverCache(response, libraryCoverCacheFile)
                if (responseCoverCache != null) {
                    return fileLoader(responseCoverCache)
                }

                snapshot = writeToDiskCache(response)
                if (snapshot != null) {
                    return SourceFetchResult(
                        source = snapshot.toImageSource(),
                        mimeType = "image/*",
                        dataSource = DataSource.NETWORK,
                    )
                }

                return SourceFetchResult(
                    source = ImageSource(source = responseBody.source(), fileSystem = FileSystem.SYSTEM),
                    mimeType = "image/*",
                    dataSource = if (response.cacheResponse != null) DataSource.DISK else DataSource.NETWORK,
                )
            } catch (e: Exception) {
                responseBody.close()
                throw e
            }
        } catch (e: Exception) {
            snapshot?.close()
            throw e
        }
    }

    private suspend fun executeNetworkRequest(): Response {
        val client = sourceLazy.value?.client ?: callFactoryLazy.value
        val response = client.newCall(newRequest()).await()
        if (!response.isSuccessful && response.code != HTTP_NOT_MODIFIED) {
            response.close()
            throw IOException(response.message)
        }
        return response
    }

    private fun newRequest(): Request {
        val request = Request.Builder().apply {
            url(url!!)

            // The whole reason a fetcher is needed rather than a plain URL: many sources answer a
            // cover request only when it carries their own headers.
            val sourceHeaders = sourceLazy.value?.headers
            if (sourceHeaders != null) {
                headers(sourceHeaders)
            }
        }

        when {
            options.networkCachePolicy.readEnabled -> request.cacheControl(CACHE_CONTROL_NO_STORE)
            else -> request.cacheControl(CACHE_CONTROL_NO_NETWORK_NO_CACHE)
        }

        return request.build()
    }

    private fun moveSnapshotToCoverCache(snapshot: DiskCache.Snapshot, cacheFile: File?): File? {
        if (cacheFile == null) return null
        return try {
            imageLoader.diskCache?.run {
                fileSystem.source(snapshot.data).use { input ->
                    writeSourceToCoverCache(input, cacheFile)
                }
                remove(diskCacheKey)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write snapshot data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeResponseToCoverCache(response: Response, cacheFile: File?): File? {
        if (cacheFile == null || !options.diskCachePolicy.writeEnabled) return null
        return try {
            response.peekBody(Long.MAX_VALUE).source().use { input ->
                writeSourceToCoverCache(input, cacheFile)
            }
            cacheFile.takeIf { it.exists() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to write response data to cover cache ${cacheFile.name}" }
            null
        }
    }

    private fun writeSourceToCoverCache(input: Source, cacheFile: File) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.delete()
        try {
            cacheFile.sink().buffer().use { output ->
                output.writeAll(input)
            }
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    private fun readFromDiskCache(): DiskCache.Snapshot? {
        return if (options.diskCachePolicy.readEnabled) {
            imageLoader.diskCache?.openSnapshot(diskCacheKey)
        } else {
            null
        }
    }

    private fun writeToDiskCache(response: Response): DiskCache.Snapshot? {
        val diskCache = imageLoader.diskCache
        val editor = diskCache?.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                response.body.source().readAll(this)
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            try {
                editor.abort()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun DiskCache.Snapshot.toImageSource(): ImageSource {
        return ImageSource(
            file = data,
            fileSystem = FileSystem.SYSTEM,
            diskCacheKey = diskCacheKey,
            closeable = this,
        )
    }

    private fun getResourceType(cover: String?): Type? {
        return when {
            cover.isNullOrEmpty() -> null
            cover.startsWith("http", true) || cover.startsWith("Custom-", true) -> Type.URL
            cover.startsWith("/") || cover.startsWith("file://") -> Type.File
            cover.startsWith("content") -> Type.URI
            else -> null
        }
    }

    private enum class Type {
        File,
        URI,
        URL,
    }

    /** For a whole [Anime], which is what the ported anime screens hand to Coil. */
    class AnimeFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<Anime> {

        private val coverCache: AnimeCoverCache by injectLazy()
        private val sourceManager: AnimeSourceManager by injectLazy()

        override fun create(data: Anime, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeCoverFetcher(
                url = data.thumbnailUrl,
                isLibraryAnime = data.favorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.thumbnailUrl) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.id) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.source) as? AnimeHttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    /** For an [AnimeCover], which is what every screen this fork wrote hands to Coil. */
    class AnimeCoverFactory(
        private val callFactoryLazy: Lazy<Call.Factory>,
    ) : Fetcher.Factory<AnimeCover> {

        private val coverCache: AnimeCoverCache by injectLazy()
        private val sourceManager: AnimeSourceManager by injectLazy()

        override fun create(data: AnimeCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return AnimeCoverFetcher(
                url = data.url,
                isLibraryAnime = data.isAnimeFavorite,
                options = options,
                coverFileLazy = lazy { coverCache.getCoverFile(data.url) },
                customCoverFileLazy = lazy { coverCache.getCustomCoverFile(data.animeId) },
                diskCacheKeyLazy = lazy { imageLoader.components.key(data, options)!! },
                sourceLazy = lazy { sourceManager.get(data.sourceId) as? AnimeHttpSource },
                callFactoryLazy = callFactoryLazy,
                imageLoader = imageLoader,
            )
        }
    }

    private companion object {
        val CACHE_CONTROL_NO_STORE = CacheControl.Builder().noStore().build()
        val CACHE_CONTROL_NO_NETWORK_NO_CACHE = CacheControl.Builder().noCache().onlyIfCached().build()

        const val HTTP_NOT_MODIFIED = 304
    }
}
