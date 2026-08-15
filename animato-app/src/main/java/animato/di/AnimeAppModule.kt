package animato.di

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import animato.anime.player.PlayerEpisodeVideoResolver
import animato.anime.services.download.EpisodeVideoResolver
import animato.anime.track.AnimeTrackerManager
import animato.data.AnimeUpdateStrategyColumnAdapter
import animato.data.FetchTypeColumnAdapter
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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
         * Mihon has since moved to AndroidxSqliteDriver over bundled SQLite, which we cannot use
         * here: that driver only accepts an async schema, and :anime:data still generates a
         * synchronous one. Following them means turning on generateAsync there first — a change
         * contained almost entirely to AndroidAnimeDatabaseHandler, and recorded as open in
         * UPSTREAM_DIVERGENCE.md. Until then this is SQLDelight's own Android driver, on the
         * version Mihon pins so the two cannot drift apart.
         */
        val driver: SqlDriver by lazy {
            AndroidSqliteDriver(
                schema = AnimeDatabase.Schema,
                context = app,
                // Unchanged from Aniyomi, so an existing install opens the database it already has.
                name = "tachiyomi.animedb",
                callback = object : AndroidSqliteDriver.Callback(AnimeDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        setPragma(db, "foreign_keys = ON")
                        setPragma(db, "journal_mode = WAL")
                        setPragma(db, "synchronous = NORMAL")
                    }

                    /**
                     * Read through a cursor, not execSQL. `PRAGMA journal_mode = WAL` answers with
                     * the mode it settled on, and Android refuses any statement that returns rows
                     * from execSQL — so that one throws, and the anime database never opens.
                     *
                     * The cursor has to be stepped: Android runs the statement when the window is
                     * filled, not when the cursor is handed out.
                     */
                    private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                        db.query("PRAGMA $pragma").use { it.moveToFirst() }
                    }
                },
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

        addSingletonFactory<AnimeDatabaseHandler> { AndroidAnimeDatabaseHandler(get(), driver) }

        addSingletonFactory { AnimeCoverCache(app) }
        addSingletonFactory { AnimeBackgroundCache(app) }

        addSingletonFactory<AnimeSourceManager> { AndroidAnimeSourceManager(app, get(), get()) }
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

        addSingletonFactory { TorrentServerApi(get(), get()) }
        addSingletonFactory { TorrentServerUtils(get(), get()) }
    }
}
