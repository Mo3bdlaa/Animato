package animato.app.entry

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.repository.TrackRepository
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One chapter or episode, in the vocabulary the shared screen draws.
 *
 * The two halves' item models differ in the same three ways they always do — `read` against `seen`,
 * `chapterNumber` against `episodeNumber`, a scanlator that only one of them really uses — so this
 * is the projection, and the screen never sees either original.
 */
@Immutable
data class EntryItem(
    val id: Long,
    val name: String,
    val number: Double,
    val scanlator: String?,
    val viewed: Boolean,
    val bookmarked: Boolean,
    val dateUpload: Long,
    val downloaded: Boolean,
)

@Immutable
data class EntryState(
    val entryId: Long,
    val contentType: ContentType,
    val isLoading: Boolean = true,
    val title: String = "",
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val statusLabel: dev.icerock.moko.resources.StringResource? = null,
    val sourceName: String = "",
    val coverData: Any? = null,
    val inLibrary: Boolean = false,
    val items: List<EntryItem> = emptyList(),
    val trackerCount: Int = 0,
) {

    /** The one the primary button resumes. First unviewed in list order, not lowest number. */
    val nextItem: EntryItem? get() = items.lastOrNull { !it.viewed }

    val viewedCount: Int get() = items.count { it.viewed }

    val hasStarted: Boolean get() = viewedCount > 0
}

/**
 * The title page, for either half.
 *
 * ## What this owns, and what it does not
 *
 * It owns the **chrome**: the backdrop header, the resume-aware primary, the in-library heart, the
 * tabs, and the list of chapters or episodes with their own states. That is the part someone sees
 * first and the part that used to be two unrelated designs — Mihon's screen for manga, the Aniyomi
 * port for anime, reached by tapping two covers that sat side by side in the same grid.
 *
 * It deliberately does **not** re-implement the deep tools each half has grown: the scanlator
 * filter, the chapter-settings dialog, per-source seasons, notes, migration, editing a cover. Those
 * are roughly six thousand lines between the two screens, most of it correct and none of it about
 * how the page looks — so the overflow opens the original screen and everything there still works.
 * Rewriting them to make one page consistent would have traded a real feature set for a nicer
 * header.
 *
 * ## Why the download state is computed here
 *
 * Neither items table records whether the files are on disk; both download caches answer that from
 * an index keyed by title and item name. So the list is joined against the cache on every emission
 * and re-joined when the cache changes, which is why a download finishing repaints the row it
 * belongs to rather than the whole screen.
 */
class EntryScreenModel(
    private val entryId: Long,
    private val contentType: ContentType,
    private val getMangaWithChapters: GetMangaWithChapters = Injekt.get(),
    private val getAnimeWithEpisodes: GetAnimeWithEpisodesAndSeasons = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    trackRepository: TrackRepository = Injekt.get(),
    animeTrackRepository: AnimeTrackRepository = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<EntryState>
        field = MutableStateFlow(EntryState(entryId = entryId, contentType = contentType))

    init {
        viewModelScope.launch {
            when (contentType) {
                ContentType.MANGA -> observeManga(trackRepository)
                ContentType.ANIME -> observeAnime(animeTrackRepository)
            }
        }
    }

    private suspend fun observeManga(trackRepository: TrackRepository) {
        combine(
            getMangaWithChapters.subscribe(entryId),
            trackRepository.getTracksByMangaIdAsFlow(entryId).map { it.size },
            downloadManager.queueState.map { },
        ) { (manga, chapters), trackers, _ ->
            val source = sourceManager.getOrStub(manga.source)
            EntryState(
                entryId = entryId,
                contentType = ContentType.MANGA,
                isLoading = false,
                title = manga.title,
                author = manga.author,
                artist = manga.artist,
                description = manga.description,
                genres = manga.genre.orEmpty(),
                statusLabel = statusLabel(manga.status),
                sourceName = source.name,
                coverData = manga.asMangaCover(),
                inLibrary = manga.favorite,
                items = withIOContext {
                    chapters.map { chapter ->
                        EntryItem(
                            id = chapter.id,
                            name = chapter.name,
                            number = chapter.chapterNumber,
                            scanlator = chapter.scanlator,
                            viewed = chapter.read,
                            bookmarked = chapter.bookmark,
                            dateUpload = chapter.dateUpload,
                            downloaded = downloadManager.isChapterDownloaded(
                                chapterName = chapter.name,
                                chapterScanlator = chapter.scanlator,
                                chapterUrl = chapter.url,
                                mangaTitle = manga.title,
                                sourceId = manga.source,
                            ),
                        )
                    }
                },
                trackerCount = trackers,
            )
        }
            .onEach { state.value = it }
            .launchIn(viewModelScope)
    }

    private suspend fun observeAnime(animeTrackRepository: AnimeTrackRepository) {
        combine(
            getAnimeWithEpisodes.subscribe(entryId),
            animeTrackRepository.getTracksByAnimeIdAsFlow(entryId).map { it.size },
            animeDownloadManager.queueState.map { },
        ) { (anime, episodes, _), trackers, _ ->
            val source = animeSourceManager.getOrStub(anime.source)
            EntryState(
                entryId = entryId,
                contentType = ContentType.ANIME,
                isLoading = false,
                title = anime.title,
                author = anime.author,
                artist = anime.artist,
                description = anime.description,
                genres = anime.genre.orEmpty(),
                statusLabel = statusLabel(anime.status),
                sourceName = source.name,
                coverData = anime.asAnimeCover(),
                inLibrary = anime.favorite,
                items = withIOContext {
                    episodes.map { episode ->
                        EntryItem(
                            id = episode.id,
                            name = episode.name,
                            number = episode.episodeNumber,
                            scanlator = episode.scanlator,
                            viewed = episode.seen,
                            bookmarked = episode.bookmark,
                            dateUpload = episode.dateUpload,
                            downloaded = animeDownloadManager.isEpisodeDownloaded(
                                episodeName = episode.name,
                                episodeScanlator = episode.scanlator,
                                animeTitle = anime.title,
                                sourceId = anime.source,
                            ),
                        )
                    }
                },
                trackerCount = trackers,
            )
        }
            .onEach { state.value = it }
            .launchIn(viewModelScope)
    }

    /**
     * The heart.
     *
     * Adding sets `favorite` and nothing else; removing does the same in reverse and keeps every
     * downloaded file, because the sweep in Settings and the library's own quick sheet are where
     * deleting is asked for rather than assumed. Categories are not asked here — Mihon's add-to-
     * category dialog is per-half and this screen answers for both.
     */
    fun toggleInLibrary() {
        val current = state.value.inLibrary
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA -> updateManga.await(MangaUpdate(id = entryId, favorite = !current))
                ContentType.ANIME -> updateAnime.await(AnimeUpdate(id = entryId, favorite = !current))
            }
        }
    }

    /**
     * Marks one item read or unread.
     *
     * Both interactors take the item itself rather than its id — they write progress and a history
     * row alongside the flag, and neither can do that from a number — so this fetches it first.
     */
    fun setViewed(item: EntryItem, viewed: Boolean) {
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA ->
                    getChapter.await(item.id)?.let { setReadStatus.await(viewed, it) }
                ContentType.ANIME ->
                    getEpisode.await(item.id)?.let { setSeenStatus.await(viewed, it) }
            }
        }
    }

    fun toggleBookmark(item: EntryItem) {
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA ->
                    updateChapter.await(ChapterUpdate(id = item.id, bookmark = !item.bookmarked))
                ContentType.ANIME ->
                    updateEpisode.await(EpisodeUpdate(id = item.id, bookmark = !item.bookmarked))
            }
        }
    }

    private fun statusLabel(status: Long) = when (status) {
        SManga.ONGOING.toLong() -> MR.strings.ongoing
        SManga.COMPLETED.toLong() -> MR.strings.completed
        SManga.LICENSED.toLong() -> MR.strings.licensed
        SManga.PUBLISHING_FINISHED.toLong() -> MR.strings.publishing_finished
        SManga.CANCELLED.toLong() -> MR.strings.cancelled
        SManga.ON_HIATUS.toLong() -> MR.strings.on_hiatus
        else -> null
    }
}
