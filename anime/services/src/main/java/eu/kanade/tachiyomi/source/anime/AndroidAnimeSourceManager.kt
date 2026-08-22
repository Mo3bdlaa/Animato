package eu.kanade.tachiyomi.source.anime

import android.content.Context
import animato.anime.iptv.M3uPlaylistStore
import animato.anime.iptv.M3uSource
import animato.anime.jellyfin.JellyfinServerStore
import animato.anime.jellyfin.JellyfinSource
import animato.anime.stremio.StremioAddonStore
import animato.anime.stremio.StremioSource
import animato.anime.torznab.TorznabIndexerStore
import animato.anime.torznab.TorznabSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

class AndroidAnimeSourceManager(
    private val context: Context,
    private val extensionManager: AnimeExtensionManager,
    private val sourceRepository: AnimeStubSourceRepository,
    private val stremioAddonStore: StremioAddonStore,
    private val m3uPlaylistStore: M3uPlaylistStore,
    private val jellyfinServerStore: JellyfinServerStore,
    private val torznabIndexerStore: TorznabIndexerStore,
) : AnimeSourceManager {

    /**
     * The four things a source can come from, in one object.
     *
     * A named holder rather than a `Triple` grown a fourth field: `combine` is arity-limited and
     * the destructuring at the collect site is positional, so an unnamed tuple of four is four
     * chances to swap two lists that are both `List<something>`.
     */
    private data class SourceInputs(
        val extensions: List<eu.kanade.tachiyomi.extension.anime.model.AnimeExtension.Installed>,
        val addons: List<animato.anime.stremio.StremioAddon>,
        val playlists: List<animato.anime.iptv.M3uPlaylist>,
        val servers: List<animato.anime.jellyfin.JellyfinServer>,
        val indexers: List<animato.anime.torznab.TorznabIndexer>,
    )

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val downloadManager: AnimeDownloadManager by injectLazy()

    /**
     * Supervised, and told what to do when something in it fails.
     *
     * With a plain `Job` and no handler this was two separate faults at once. A throw in any child
     * — and the children read the database and construct sources — was an uncaught exception at
     * startup, so the app died. And because siblings of a plain Job cancel together, one failing
     * stub-source write also killed the collector that maintains the source map, which on screen
     * is every source the person has disappearing at once.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, error ->
                logcat(LogPriority.ERROR, error) { "Anime source manager failed" }
            },
    )

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, AnimeSource>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubAnimeSource>()

    override val sources: Flow<List<AnimeSource>> = sourcesMapFlow.map { it.values.toList() }

    init {
        scope.launch {
            // Three kinds of source, one map. Extensions are code we installed and run; Stremio
            // addons are URLs we only ever talk to; M3U playlists are a file we read. Everything
            // downstream — browsing, search, the library, downloads — asks this manager for a
            // source by id and neither knows nor needs to know which kind answered.
            combine(
                extensionManager.installedExtensionsFlow,
                stremioAddonStore.addons,
                m3uPlaylistStore.playlists,
                jellyfinServerStore.servers,
                torznabIndexerStore.indexers,
            ) { extensions, addons, playlists, servers, indexers ->
                SourceInputs(extensions, addons, playlists, servers, indexers)
            }
                .collectLatest { (extensions, addons, playlists, servers, indexers) ->
                    val mutableMap = ConcurrentHashMap<Long, AnimeSource>(
                        mapOf(
                            LocalAnimeSource.ID to LocalAnimeSource(
                                context,
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                                Injekt.get(),
                            ),
                        ),
                    )
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            registerStubSource(StubAnimeSource.from(it))
                        }
                    }
                    // Only the addons that have something to browse become sources. A stream-only
                    // addon is still installed and still consulted for video, but as a source it
                    // would be empty shelves and an empty search — which reads as broken rather
                    // than as the supporting role it actually plays.
                    addons.filter { it.isBrowsable }.forEach { addon ->
                        val source = StremioSource(addon)
                        mutableMap[source.id] = source
                        registerStubSource(StubAnimeSource.from(source))
                    }
                    // Every playlist is browsable by definition — a playlist that parsed into no
                    // channels was refused when it was added, so there is no empty-shelf case to
                    // filter out the way there is for a stream-only addon.
                    playlists.forEach { playlist ->
                        val source = M3uSource(playlist)
                        mutableMap[source.id] = source
                        registerStubSource(StubAnimeSource.from(source))
                    }
                    // Every signed-in server is browsable: a sign-in is the only way one gets here
                    // and a server with nothing in it is an empty library rather than a broken
                    // source, so there is no equivalent of the stream-only addon to filter out.
                    servers.forEach { server ->
                        val source = JellyfinSource(server)
                        mutableMap[source.id] = source
                        registerStubSource(StubAnimeSource.from(source))
                    }
                    // An indexer is browsable by definition too: adding one is a `t=caps` request
                    // that had to succeed, so there is no unusable-but-installed state here either.
                    indexers.forEach { indexer ->
                        val source = TorznabSource(indexer)
                        mutableMap[source.id] = source
                        registerStubSource(StubAnimeSource.from(source))
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            sourceRepository.subscribeAllAnime()
                .collectLatest { sources ->
                    val mutableMap = stubSourcesMap.toMutableMap()
                    sources.forEach {
                        mutableMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): AnimeSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): AnimeSource {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    override fun getAll(): List<AnimeSource> = sourcesMapFlow.value.values.toList()

    override fun getOnlineSources(): List<AnimeHttpSource> {
        return sourcesMapFlow.value.values.filterIsInstance<AnimeHttpSource>()
    }

    override fun getStubSources(): List<StubAnimeSource> {
        val onlineSourceIds = getAll().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubAnimeSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubAnimeSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubAnimeSource {
        sourceRepository.getStubAnimeSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubAnimeSource(id = id, lang = "", name = "")
    }
}
