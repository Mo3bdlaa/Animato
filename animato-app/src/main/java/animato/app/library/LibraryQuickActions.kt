package animato.app.library

import androidx.compose.runtime.Immutable
import animato.domain.content.ContentType
import animato.domain.content.LibraryEntry
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * What the quick sheet knows about one entry, once it has looked.
 *
 * Everything here is a *consequence*, not a label: the next item's own name, how many rows "mark
 * done up to here" would change, how many a download would queue, what a removal keeps. The sheet's
 * captions state what each action will do, and none of that is in the library row — a library row
 * knows counts, not which chapter is next — so opening the sheet is a read.
 */
@Immutable
data class QuickSheetState(
    val entry: LibraryEntry,
    val isLoading: Boolean = true,
    val nextItemId: Long? = null,
    val nextItemName: String? = null,
    val unviewedCount: Int = 0,
    val downloadedCount: Int = 0,
) {
    val canContinue: Boolean get() = nextItemId != null
}

/**
 * The five things you can do to a library entry without opening it.
 *
 * ## Why this is its own class
 *
 * Every one of these actions is the same operation written twice — once against `Chapter`,
 * `SetReadStatus` and `DownloadManager`, once against `Episode`, `SetSeenStatus` and
 * `AnimeDownloadManager` — because the two halves have parallel types that no interface joins.
 * Ten interactors and a `when` per method is a lot to add to a screen model that is otherwise about
 * filtering a list, so the split lives here and [UnifiedLibraryScreenModel] delegates.
 *
 * ## The one thing that is not symmetric
 *
 * Removing an entry drops its covers from the cover cache, and the two halves keep those in
 * different caches with different call sites — the anime one also has backgrounds. That is handled
 * by each half's own library screen and is deliberately *not* reproduced here: this sets `favorite`
 * to false and nothing else, so nothing of the user's is deleted by a tap. Downloads are deleted
 * only when they ask, which is what the sheet's confirmation is for.
 */
class LibraryQuickActions(
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getChapters: GetChaptersByMangaId = Injekt.get(),
    private val getEpisodes: GetEpisodesByAnimeId = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
) {

    /**
     * Reads what the sheet needs to caption itself.
     *
     * "Next" is the first unviewed item in source order, which is the same thing both halves' own
     * resume buttons mean by it — not the lowest chapter number, because a source that numbers its
     * chapters oddly would otherwise send you backwards.
     */
    suspend fun inspect(entry: LibraryEntry): QuickSheetState = withIOContext {
        when (entry.contentType) {
            ContentType.MANGA -> {
                val chapters = getChapters.await(entry.entryId).sortedBy { it.sourceOrder }
                val next = chapters.firstOrNull { !it.read }
                QuickSheetState(
                    entry = entry,
                    isLoading = false,
                    nextItemId = next?.id,
                    nextItemName = next?.name,
                    unviewedCount = chapters.count { !it.read },
                    downloadedCount = getManga.await(entry.entryId)
                        ?.let { downloadManager.getDownloadCount(it) }
                        ?: 0,
                )
            }
            ContentType.ANIME -> {
                val episodes = getEpisodes.await(entry.entryId).sortedBy { it.sourceOrder }
                val next = episodes.firstOrNull { !it.seen }
                QuickSheetState(
                    entry = entry,
                    isLoading = false,
                    nextItemId = next?.id,
                    nextItemName = next?.name,
                    unviewedCount = episodes.count { !it.seen },
                    downloadedCount = getAnime.await(entry.entryId)
                        ?.let { animeDownloadManager.getDownloadCount(it) }
                        ?: 0,
                )
            }
        }
    }

    /**
     * Everything before the next unviewed item, marked done.
     *
     * *Up to here* is the point you have reached, so this marks what is behind it and leaves the
     * next item alone — the action is for catching a shelf up with reality, not for declaring
     * something finished you have not opened.
     */
    suspend fun markDoneUpToHere(entry: LibraryEntry) = withIOContext {
        when (entry.contentType) {
            ContentType.MANGA -> {
                val chapters = getChapters.await(entry.entryId).sortedBy { it.sourceOrder }
                val cutoff = chapters.indexOfFirst { !it.read }.takeIf { it > 0 } ?: return@withIOContext
                setReadStatus.await(true, *chapters.take(cutoff).toTypedArray())
            }
            ContentType.ANIME -> {
                val episodes = getEpisodes.await(entry.entryId).sortedBy { it.sourceOrder }
                val cutoff = episodes.indexOfFirst { !it.seen }.takeIf { it > 0 } ?: return@withIOContext
                setSeenStatus.await(true, *episodes.take(cutoff).toTypedArray())
            }
        }
    }

    /** The next [count] unviewed items, queued. A source that is gone queues nothing. */
    suspend fun downloadNext(entry: LibraryEntry, count: Int) = withIOContext {
        when (entry.contentType) {
            ContentType.MANGA -> {
                val manga = getManga.await(entry.entryId) ?: return@withIOContext
                val chapters = getChapters.await(entry.entryId)
                    .sortedBy { it.sourceOrder }
                    .filter { !it.read }
                    .take(count)
                if (chapters.isNotEmpty()) downloadManager.downloadChapters(manga, chapters)
            }
            ContentType.ANIME -> {
                val anime = getAnime.await(entry.entryId) ?: return@withIOContext
                val episodes = getEpisodes.await(entry.entryId)
                    .sortedBy { it.sourceOrder }
                    .filter { !it.seen }
                    .take(count)
                if (episodes.isNotEmpty()) animeDownloadManager.downloadEpisodes(anime, episodes)
            }
        }
    }

    /**
     * Off the shelf, with the files kept unless asked otherwise.
     *
     * Two separate decisions, and the sheet asks them separately, because they are not the same
     * regret: taking something out of a library is trivially undone by adding it again, and
     * deleting six hundred megabytes is not.
     */
    suspend fun remove(entry: LibraryEntry, deleteDownloads: Boolean) = withIOContext {
        when (entry.contentType) {
            ContentType.MANGA -> {
                updateManga.await(MangaUpdate(id = entry.entryId, favorite = false))
                if (deleteDownloads) {
                    val manga = getManga.await(entry.entryId) ?: return@withIOContext
                    val chapters = getChapters.await(entry.entryId)
                    downloadManager.deleteChapters(chapters, manga, sourceOf(manga.source) ?: return@withIOContext)
                }
            }
            ContentType.ANIME -> {
                updateAnime.await(AnimeUpdate(id = entry.entryId, favorite = false))
                if (deleteDownloads) {
                    val anime = getAnime.await(entry.entryId) ?: return@withIOContext
                    val episodes = getEpisodes.await(entry.entryId)
                    animeDownloadManager.deleteEpisodes(
                        episodes,
                        anime,
                        animeSourceOf(anime.source) ?: return@withIOContext,
                    )
                }
            }
        }
    }

    /*
     * A stub is enough, and `get` was not.
     *
     * Deleting downloads needs the source only for the name of its folder, which a stub answers.
     * `get` returns null for an extension that has been uninstalled — so "remove and delete the
     * downloads" un-favourited the entry and then abandoned the deletion, keeping the files. That
     * is exactly backwards: an entry whose extension is gone is the most likely one to be removed,
     * and the one whose files are least likely ever to be wanted again.
     */
    private fun sourceOf(sourceId: Long) =
        Injekt.get<tachiyomi.domain.source.service.SourceManager>().getOrStub(sourceId)

    private fun animeSourceOf(sourceId: Long) =
        Injekt.get<tachiyomi.domain.source.anime.service.AnimeSourceManager>().getOrStub(sourceId)

    companion object {
        /**
         * How many "download next" queues.
         *
         * A number rather than "all", because the action exists for the case where you are about to
         * be offline for a train ride and not for the case where you want a whole series — that one
         * is the title page's own download menu, which can say how many.
         */
        const val DOWNLOAD_BATCH = 5
    }
}
