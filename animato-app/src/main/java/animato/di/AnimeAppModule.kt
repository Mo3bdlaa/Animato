package animato.di

import android.app.Application
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import animato.anime.player.PlayerEpisodeVideoResolver
import animato.anime.player.cast.CastController
import animato.anime.services.download.EpisodeVideoResolver
import animato.anime.stremio.StremioAddonStore
import animato.anime.track.AnimeTrackerManager
import animato.app.discover.MetadataCatalog
import animato.data.AnimeUpdateStrategyColumnAdapter
import animato.data.FetchTypeColumnAdapter
import aniyomi.core.common.torrent.AppTorrentInfoProvider
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.source.anime.AndroidAnimeSourceManager
import eu.kanade.tachiyomi.torrentutils.TorrentInfoProvider
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.mi.data.AnimeDatabase
import tachiyomi.source.local.entries.anime.LocalAnimeFetchTypeManager
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * The anime half of Mihon's AppModule: the anime database, the managers and the caches.
 *
 * The two databases stay separate, on separate drivers and separate files. Merging them would
 * forfeit every future Mihon migration, which is the one thing this whole architecture exists to
 * keep — see ARCHITECTURE.md.
 */
class AnimeAppModule(val app: Application) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        /*
         * Mihon binds its own driver as SqlDriver, so this one stays a local: two bindings of the
         * same type would collide, and nothing outside this module needs the anime driver.
         *
         * `by lazy` rather than a plain val so that registering the module does not open the
         * database.
         *
         * Bundled SQLite, exactly as Mihon opens its own, and not for symmetry: on the device's own
         * SQLite these queries did not all mean the same thing. `minSdk` is 26, three of the anime
         * `.sq` files upsert with ON CONFLICT … DO UPDATE, and SQLite only learned that in 3.24 —
         * Android 10. On 8 and 9 they were a syntax error at execution, and the first of them runs
         * every time an episode is watched. A SQLite that travels with the app has one behaviour
         * everywhere, which is the whole reason Mihon carries it and Aniyomi carried requery.
         *
         * No pragma callback any more. All three of the ones it used to set are the driver's now,
         * checked against the artifact rather than assumed: `journalMode` defaults to WAL and
         * `sync` to Normal, and foreign keys are the one that does *not* default on — hence the
         * configuration below, which is also the only line Mihon passes.
         *
         * The file name is Aniyomi's, unchanged, so an existing install opens the database it
         * already has and migrates from the version recorded in it.
         */
        val driver: SqlDriver by lazy {
            AndroidxSqliteDriver(
                driver = BundledSQLiteDriver(),
                databaseType = AndroidxSqliteDatabaseType.FileProvider(app, "tachiyomi.animedb"),
                schema = AnimeDatabase.Schema,
                configuration = AndroidxSqliteConfiguration(
                    isForeignKeyConstraintsEnabled = true,
                ),
            )
        }

        addSingletonFactory {
            AnimeDatabase(
                driver = driver,
                animehistoryAdapter = Animehistory.Adapter(
                    last_seenAdapter = DateColumnAdapter,
                ),
                animesAdapter = Animes.Adapter(
                    genreAdapter = StringListColumnAdapter,
                    update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                    fetch_typeAdapter = FetchTypeColumnAdapter,
                    memoAdapter = MemoColumnAdapter,
                ),
                episodesAdapter = Episodes.Adapter(
                    memoAdapter = MemoColumnAdapter,
                ),
            )
        }

        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get()) }

        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }

        // The addon store comes first because the source manager reads its stored addons on the
        // very first emission: a source list that starts short and grows a moment later is a
        // source list people have already scrolled past.
        addSingletonFactory { StremioAddonStore() }
        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get(), get()) }
        addSingletonFactory { AnimeExtensionManager(app) }

        // The anime half of the trackers. Each wraps the Mihon tracker of the same id and shares
        // its credentials, so Mihon's own manager has to exist first — which it does, since Mihon
        // registers it in its own module and this one runs after.
        addSingletonFactory { AnimeTrackerManager(get()) }

        addSingletonFactory { AnimeDownloadProvider(app) }
        addSingletonFactory { AnimeDownloadManager(app) }
        addSingletonFactory { AnimeDownloadCache(app) }

        addSingletonFactory { LocalAnimeSourceFileSystem(get()) }
        addSingletonFactory { LocalAnimeBackgroundManager(app, get()) }
        addSingletonFactory { LocalAnimeCoverManager(app, get()) }
        addSingletonFactory { LocalAnimeFetchTypeManager(app, get()) }
        addSingletonFactory { LocalEpisodeThumbnailManager(app, get()) }

        // The downloader asks for this by interface; the player is what can answer.
        addSingletonFactory<EpisodeVideoResolver> { PlayerEpisodeVideoResolver() }

        // Discover's public rails. A singleton so the three rails on one screen share a client.
        addSingletonFactory { MetadataCatalog(get()) }

        // One cast session for the app, and deliberately not scoped to the player: casting is the
        // case where the phone gets put down, so the session has to outlive the activity that
        // started it.
        addSingletonFactory { CastController() }

        addSingletonFactory { TorrentServerApi(get(), get()) }
        addSingletonFactory { TorrentServerUtils(get(), get()) }

        // What TorrentUtils in the extension API resolves at runtime. Without this registration,
        // every torrent-backed source dies at video time on an unresolvable injection.
        addSingletonFactory<TorrentInfoProvider> { AppTorrentInfoProvider(get(), get()) }
    }
}
