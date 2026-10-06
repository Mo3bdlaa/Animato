package animato.di

import android.app.Application
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import animato.anime.di.AnimatoScope
import animato.data.AnimeUpdateStrategyColumnAdapter
import animato.data.FetchTypeColumnAdapter
import animato.domain.content.ContentPreferences
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.FileProvider
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.mo3bdlaa.animato.BuildConfig
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.mi.data.AnimeDatabase

/**
 * The anime bindings that cannot be an `@Inject` where they are declared.
 */
@BindingContainer
object AnimeBindings {

    /**
     * The anime database: its own file, its own driver, never merged with Mihon's — merging would
     * forfeit every future Mihon migration, which is the one thing this architecture exists to keep
     * (see ARCHITECTURE.md).
     *
     * The driver is not a binding of its own: Mihon's graph binds its driver as `SqlDriver`, and
     * nothing outside this function needs the anime one.
     *
     * Bundled SQLite, exactly as Mihon opens its own, and not for symmetry: `minSdk` is 26, three of
     * the anime `.sq` files upsert with ON CONFLICT … DO UPDATE, and the device's SQLite only learned
     * that in 3.24 — Android 10. On 8 and 9 they were a syntax error at execution, and the first of
     * them runs every time an episode is watched. Foreign keys are the one setting the driver does
     * not default on, hence the configuration. The file name is Aniyomi's, so an existing install
     * opens the database it already has and migrates from the version recorded in it.
     */
    @Provides
    @SingleIn(AnimatoScope::class)
    fun animeDatabase(application: Application): AnimeDatabase = AnimeDatabase(
        driver = AndroidxSqliteDriver(
            driver = BundledSQLiteDriver(),
            databaseType = AndroidxSqliteDatabaseType.FileProvider(application, "tachiyomi.animedb"),
            schema = AnimeDatabase.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
            ),
        ),
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

    /**
     * The lens, and whether this build gets to choose it.
     *
     * The television build carries no manga native libraries, so manga is not a thing it can
     * decline to show — it is a thing it cannot show. That is decided here, where the build's own
     * BuildConfig is readable: `:anime:domain` holds ContentPreferences and has no BuildConfig.
     */
    @Provides
    @SingleIn(AnimatoScope::class)
    fun contentPreferences(preferenceStore: PreferenceStore): ContentPreferences =
        ContentPreferences(preferenceStore, animeOnly = BuildConfig.ANIMATO_ANIME_ONLY)
}
