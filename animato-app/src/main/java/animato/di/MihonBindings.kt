package animato.di

import android.content.Context
import animato.anime.di.AnimatoScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.saver.ImageSaver
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.data.Database
import tachiyomi.data.backup.RestoreRepositoryImpl
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.data.updates.UpdatesRepositoryImpl
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.domain.updates.repository.UpdatesRepository

/**
 * Mihon's types the anime side needs and Mihon's `AppGraph` does not expose.
 *
 * `AppGraph` exposes forty-odd types. Everything else Mihon builds lives inside its generated graph
 * where nothing outside can reach it — not without reflection, which is what this replaced, and
 * which R8 broke in the release build. So these are built here instead, and every one of them is
 * a second instance of something Mihon also holds. That is only acceptable because each is a thin
 * wrapper with no state of its own worth sharing:
 *
 * - **The repositories** hold nothing but the database, and the database they get is Mihon's own
 *   instance ([AnimatoGraph.Factory] takes it). Same connection, same SQLDelight listeners — a
 *   manga written through one of these shows up in Mihon's screens immediately, and vice versa.
 * - **The preference store** wraps the default SharedPreferences, which Android already keeps one
 *   of per process. Two stores read and write the same values and both see every change.
 * - **[StorageManager]** derives folders from a preference and creates them if missing; two of
 *   them agree, because they read the same preference.
 * - **[DownloadProvider]** and **[ImageSaver]** compute paths and write files; they keep no state.
 *
 * Anything that *does* hold state — the download queue, the source and extension managers, the
 * trackers, the caches — `AppGraph` exposes, and those are Mihon's own instances. If an anime class
 * ever needs one that `AppGraph` does not expose and that is not as harmless as these, it does not
 * belong here: it needs a way to reach Mihon's instance, and the build failing until one exists is
 * correct.
 */
@BindingContainer
object MihonBindings {

    @Provides
    @SingleIn(AnimatoScope::class)
    fun preferenceStore(context: Context): PreferenceStore = AndroidPreferenceStore(context)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun storageManager(context: Context, storagePreferences: StoragePreferences): StorageManager =
        StorageManager(context, storagePreferences)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun downloadProvider(
        context: Context,
        storageManager: StorageManager,
        libraryPreferences: LibraryPreferences,
    ): DownloadProvider = DownloadProvider(context, storageManager, libraryPreferences)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun imageSaver(context: Context): ImageSaver = ImageSaver(context)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun mangaRepository(database: Database): MangaRepository = MangaRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun chapterRepository(database: Database): ChapterRepository = ChapterRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun categoryRepository(database: Database): CategoryRepository = CategoryRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun historyRepository(database: Database): HistoryRepository = HistoryRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun trackRepository(database: Database): TrackRepository = TrackRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun updatesRepository(database: Database): UpdatesRepository = UpdatesRepositoryImpl(database)

    @Provides
    @SingleIn(AnimatoScope::class)
    fun restoreRepository(
        database: Database,
        mangaRepository: MangaRepository,
        chapterRepository: ChapterRepository,
        trackRepository: TrackRepository,
    ): RestoreRepository = RestoreRepositoryImpl(database, mangaRepository, chapterRepository, trackRepository)
}
