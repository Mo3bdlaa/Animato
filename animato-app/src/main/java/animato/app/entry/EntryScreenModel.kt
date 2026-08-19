package animato.app.entry

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.player.describeForUser
import animato.domain.content.ContentType
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.model.applyFilters
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.service.missingChaptersCount
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.service.missingEntriesCount
import tachiyomi.domain.manga.interactor.GetManga
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue

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
    /**
     * Whether this row is a season rather than something to watch.
     *
     * A season is another entry, with its own episode list, and tapping it has to open that entry
     * instead of the player. Carried on the item rather than read from the anime at the tap site so
     * that a list is never half one kind and half the other by accident.
     */
    val isSeason: Boolean = false,
)

/**
 * What asking the source produced, as something to say rather than something to infer.
 *
 * A refresh that finds nothing and a refresh that failed both leave the list exactly as it was, so
 * without this the button is indistinguishable from a button that does nothing — which is what a
 * device reported about the control that used to sit in its place.
 */
@Immutable
sealed interface RefreshResult {
    @Immutable
    data class Found(val count: Int) : RefreshResult

    @Immutable
    data object UpToDate : RefreshResult

    @Immutable
    data class Failed(val message: String?) : RefreshResult
}

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
    /**
     * The title before any correction, kept so the editor can show what it is departing from and
     * offer to return to it. Without it "reset" would have nothing to reset to until a refresh.
     */
    val sourceTitle: String = "",
    val override: EntryOverride? = null,
    val statusLabel: dev.icerock.moko.resources.StringResource? = null,
    val sourceName: String = "",
    /**
     * Which source this entry came from.
     *
     * Carried for the tracking sheet, which takes a source id: what it does with it is decide
     * whether the source is one of the trackers in disguise — a Komga or a Jellyfin server, where
     * the link is the entry's own url and there is nothing to search for.
     */
    val sourceId: Long = 0L,
    /**
     * The entry's page on the source's own site, when it has one.
     *
     * Null for a local entry and for a source that is a stub because its extension is gone — in
     * both cases there is no site to open, and a button that opens nothing is worse than no button.
     */
    val webViewUrl: String? = null,
    val coverData: Any? = null,
    val inLibrary: Boolean = false,
    val items: List<EntryItem> = emptyList(),
    /**
     * Whether [items] runs newest-first, which is the entry's own setting rather than a constant.
     *
     * Carried because it decides which end of the list *resume* means: descending, the next unread
     * is the last one that matches; ascending, the first. Getting it from the list would need the
     * list to be trusted to be sorted, which is exactly what was not true.
     */
    val sortDescending: Boolean = true,
    /**
     * Whether the source has ever been asked about this entry.
     *
     * False for a row that only exists because something was tapped in a source list or a search
     * result: `networkToLocal*` writes a title and a url and nothing else. See the first-fetch in
     * the model, which is what stops that landing on an empty page.
     */
    val initialized: Boolean = false,
    /** Numbers the source skipped or a filter removed, counted the way both halves already count. */
    val missingCount: Int = 0,
    /**
     * Days until the next release the library update job predicts, or null when it has none.
     *
     * Null covers three honest cases at once: a completed work, an entry not in the library — the
     * prediction is computed by the update job and nothing else — and one the job has not seen
     * enough releases of to guess.
     */
    val nextReleaseDays: Int? = null,
    /** How often releases have been arriving, in days. Absolute: a user-set interval is negative. */
    val releaseIntervalDays: Int? = null,
    val trackerCount: Int = 0,
    val isRefreshing: Boolean = false,
    /** What the last refresh found, held until the screen shows it once. */
    val refreshResult: RefreshResult? = null,
) {

    /**
     * The one the primary button resumes: the earliest unviewed item, whichever way the list runs.
     *
     * It used to be `items.lastOrNull { !viewed }` unconditionally, which is right only for a
     * newest-first list — and the list was in no particular order at all, so on an ascending entry
     * this offered to resume from the newest chapter instead of the oldest unread one.
     */
    val nextItem: EntryItem?
        get() = if (sortDescending) items.lastOrNull { !it.viewed } else items.firstOrNull { !it.viewed }

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
    private val getManga: GetManga = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val entryOverrides: EntryOverrides = Injekt.get(),
    private val updateMangaFromRemote: UpdateMangaFromRemote = Injekt.get(),
    private val updateAnimeFromRemote: UpdateAnimeFromRemote = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val addAnimeTracks: AddAnimeTracks = Injekt.get(),
    trackRepository: TrackRepository = Injekt.get(),
    animeTrackRepository: AnimeTrackRepository = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<EntryState>
        field = MutableStateFlow(EntryState(entryId = entryId, contentType = contentType))

    private var firstFetchDone = false

    init {
        viewModelScope.launch {
            when (contentType) {
                ContentType.MANGA -> observeManga(trackRepository)
                ContentType.ANIME -> observeAnime(animeTrackRepository)
            }
        }

        /*
         * Ask the source once, for an entry nobody has ever asked about.
         *
         * Opening something from a source list creates its row through `networkToLocal*`, which
         * stores a title and a url — no description, no chapters, no episodes. The page observes
         * the database, so it drew exactly that: a title over an empty list, until the refresh
         * button was pressed by hand. From a device: *"when I open anything from a source the page
         * doesn't load and I have to hit refresh."*
         *
         * Once, and only for an uninitialised entry: a library entry has been fetched before and a
         * page that re-asks the source every time it is opened is a page that costs a request per
         * glance.
         */
        viewModelScope.launch {
            state.first { !it.isLoading }
            if (!firstFetchDone && !state.value.initialized) {
                firstFetchDone = true
                // Quietly: nobody pressed anything. A source that fails here has already said so
                // by leaving the page empty, and a snackbar on arrival — for a request the user
                // did not make — is the app talking over its own screen. The refresh button
                // reports properly, because that one was asked for.
                refresh(announce = false)
            }
        }
    }

    private suspend fun observeManga(trackRepository: TrackRepository) {
        combine(
            // With the scanlator filter, which the default omits: a scanlator excluded in the
            // chapter settings was still being listed here.
            getMangaWithChapters.subscribe(entryId, applyScanlatorFilter = true),
            trackRepository.getTracksByMangaIdAsFlow(entryId).map { it.size },
            downloadManager.queueState.map { },
            entryOverrides.overrides,
        ) { (manga, chapters), trackers, _, overrides ->
            val source = sourceManager.getOrStub(manga.source)
            val edited = overrides[EntryOverrides.key(ContentType.MANGA, entryId)]
            // The entry's own sort and filters, which this page applied none of. See the class note.
            val ordered = withIOContext { chapters.applyFilters(manga, downloadManager) }
            EntryState(
                entryId = entryId,
                contentType = ContentType.MANGA,
                isLoading = false,
                title = edited?.title ?: manga.title,
                author = edited?.author ?: manga.author,
                artist = edited?.artist ?: manga.artist,
                description = edited?.description ?: manga.description,
                genres = edited?.genres ?: manga.genre.orEmpty(),
                sourceTitle = manga.title,
                override = edited,
                statusLabel = statusLabel(manga.status),
                sourceName = source.name,
                sourceId = manga.source,
                webViewUrl = (source as? HttpSource)?.runCatching { getMangaUrl(manga.toSManga()) }?.getOrNull(),
                coverData = manga.asMangaCover(),
                inLibrary = manga.favorite,
                sortDescending = manga.sortDescending(),
                initialized = manga.initialized,
                missingCount = ordered.map { it.chapterNumber }.missingChaptersCount(),
                nextReleaseDays = daysUntil(manga.expectedNextUpdate?.toEpochMilliseconds()),
                releaseIntervalDays = manga.fetchInterval.absoluteValue.takeIf { it > 0 },
                items = withIOContext {
                    ordered.map { chapter ->
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
            entryOverrides.overrides,
        ) { (anime, episodes, seasons), trackers, _, overrides ->
            val source = animeSourceManager.getOrStub(anime.source)
            val edited = overrides[EntryOverrides.key(ContentType.ANIME, entryId)]
            val ordered = withIOContext { episodes.applyFilters(anime, animeDownloadManager) }
            EntryState(
                entryId = entryId,
                contentType = ContentType.ANIME,
                isLoading = false,
                title = edited?.title ?: anime.title,
                author = edited?.author ?: anime.author,
                artist = edited?.artist ?: anime.artist,
                description = edited?.description ?: anime.description,
                genres = edited?.genres ?: anime.genre.orEmpty(),
                sourceTitle = anime.title,
                override = edited,
                statusLabel = statusLabel(anime.status),
                sourceName = source.name,
                sourceId = anime.source,
                webViewUrl = (source as? AnimeHttpSource)?.runCatching { getAnimeUrl(anime.toSAnime()) }?.getOrNull(),
                coverData = anime.asAnimeCover(),
                inLibrary = anime.favorite,
                sortDescending = anime.sortDescending(),
                initialized = anime.initialized,
                missingCount = ordered.map { it.episodeNumber }.missingEntriesCount(),
                nextReleaseDays = daysUntil(anime.expectedNextUpdate?.toEpochMilli()),
                releaseIntervalDays = anime.fetchInterval.absoluteValue.takeIf { it > 0 },
                items = withIOContext {
                    // A series whose source splits it into seasons has no episodes of its own —
                    // each season is a separate entry carrying its own. The list was being built
                    // from the empty side, so those titles showed a blank page and looked like a
                    // source that had stopped working. The seasons were already being fetched and
                    // then discarded one line above.
                    if (anime.fetchType == FetchType.Seasons) {
                        seasons
                            .sortedBy { it.anime.seasonNumber }
                            .map { season ->
                                EntryItem(
                                    id = season.id,
                                    name = season.anime.title,
                                    number = season.anime.seasonNumber,
                                    scanlator = null,
                                    // Seen when every episode in it is, which is the only one of
                                    // these flags that means anything for a whole season.
                                    viewed = season.seen,
                                    bookmarked = false,
                                    dateUpload = season.latestUpload,
                                    downloaded = false,
                                    isSeason = true,
                                )
                            }
                    } else {
                        ordered.map { episode ->
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
     *
     * ## The trackers that match themselves
     *
     * Adding also offers the entry to the enhanced trackers, which is the one piece of the original
     * screens' add that could not be left out. A Komga, Kavita, Suwayomi or Jellyfin entry is
     * already on that server's list — the tracker's whole job is to notice, by url, without anybody
     * searching for anything — and both original screens do this on add. This page did not, so a
     * title added from here was the one title on the server that never got its row, and the
     * difference depended on which page you happened to press the heart from.
     *
     * Only on the way in. Removing from the library deliberately leaves the track row alone, the
     * same as everywhere else: the server's list is not this app's to unpick.
     */
    fun toggleInLibrary() {
        val current = state.value.inLibrary
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA -> {
                    updateManga.await(MangaUpdate(id = entryId, favorite = !current))
                    if (!current) {
                        val manga = getManga.await(entryId) ?: return@launchNonCancellable
                        addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
                    }
                }
                ContentType.ANIME -> {
                    updateAnime.await(AnimeUpdate(id = entryId, favorite = !current))
                    if (!current) {
                        val anime = getAnime.await(entryId) ?: return@launchNonCancellable
                        addAnimeTracks.bindEnhancedTrackers(
                            anime,
                            animeSourceManager.getOrStub(anime.source),
                        )
                    }
                }
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

    /**
     * Keep a correction, or drop it.
     *
     * Blank fields arrive here as null rather than as empty strings, and the difference is the
     * whole design: a cleared field means *follow the source again*, which is a different
     * instruction from *this entry has no author*. Nothing is written to the entry's own row — see
     * [EntryOverrides] for why it cannot be.
     */
    fun saveOverride(override: EntryOverride) {
        entryOverrides.set(state.value.contentType, entryId, override)
        viewModelScope.launchIO { applyOverride() }
    }

    fun clearOverride() {
        entryOverrides.clear(state.value.contentType, entryId)
        viewModelScope.launchIO { refresh(announce = false) }
    }

    /**
     * Write the correction into the entry's own row as well as keeping it.
     *
     * The store is the source of truth; this is a copy, and it exists for the screens this project
     * does not own. The library grid and the updates feed render through the upstream components,
     * which read the row and know nothing about an override applied on the way out — so a renamed
     * entry would keep its old name everywhere except the page it was renamed on, which reads as
     * the rename not having worked.
     *
     * The copy is not permanent and does not have to be: a refresh from the source overwrites these
     * columns, and [applyOverride] runs again after every refresh this screen performs. Anything
     * clobbered by a background library update is repaired the next time the entry is opened. The
     * alternative — teaching the refresh to spare edited fields — is only available on the anime
     * half, since the manga interactor belongs to Mihon.
     */
    private suspend fun applyOverride() {
        val override = entryOverrides.get(state.value.contentType, entryId) ?: return
        when (state.value.contentType) {
            ContentType.MANGA -> updateManga.await(
                MangaUpdate(
                    id = entryId,
                    title = override.title,
                    author = override.author,
                    artist = override.artist,
                    description = override.description,
                    genre = override.genres,
                ),
            )
            ContentType.ANIME -> updateAnime.await(
                AnimeUpdate(
                    id = entryId,
                    title = override.title,
                    author = override.author,
                    artist = override.artist,
                    description = override.description,
                    genre = override.genres,
                ),
            )
        }
    }

    /**
     * Asks the source whether anything new exists.
     *
     * ## Why this had to exist
     *
     * The page had no refresh at all. The circular-arrows icon in its place opened the original
     * screen, which is a different thing entirely — from a device: *"when I press the tracker icon,
     * expecting it to check whether there is anything new, it opens the old page instead."* The
     * glyph was right about what it promised and wrong about what it did, so the promise is what
     * got kept.
     *
     * ## Both halves, one interactor each
     *
     * `UpdateMangaFromRemote` and `UpdateAnimeFromRemote` are the same paths the library update job
     * runs, so a manual refresh and an automatic one agree about what counts as new, what happens to
     * a renamed title, and when a cover is re-fetched. `manualFetch = true` is what distinguishes
     * them: it re-downloads the cover and ignores the update strategy, because somebody asking by
     * hand has usually asked *because* the automatic answer looked wrong.
     *
     * The result is reported rather than left to be inferred. Nothing new and a source that threw
     * both leave the list identical, and a button whose success case is indistinguishable from its
     * failure case is the thing this replaced.
     */
    fun refresh(announce: Boolean = true) {
        if (state.value.isRefreshing) return
        state.update { it.copy(isRefreshing = true, refreshResult = null) }

        viewModelScope.launchIO {
            val result = when (contentType) {
                ContentType.MANGA -> getManga.await(entryId)?.let { manga ->
                    updateMangaFromRemote(
                        manga = manga,
                        fetchDetails = true,
                        fetchChapters = true,
                        manualFetch = true,
                    ).map { it.newChapters.size }
                }
                ContentType.ANIME -> getAnime.await(entryId)?.let { anime ->
                    updateAnimeFromRemote.awaitEpisodesUpdate(
                        anime = anime,
                        fetchDetails = true,
                        fetchEpisodes = true,
                        manualFetch = true,
                    ).map { it.newEpisodes.size }
                }
            }

            // The refresh has just written the source's own values over any correction, so put the
            // correction back before anything renders the row again.
            applyOverride()

            state.update {
                it.copy(
                    isRefreshing = false,
                    refreshResult = if (!announce) {
                        null
                    } else {
                        when {
                            // The entry vanished from the database while the refresh was in flight,
                            // which is what removing it from another screen looks like from here.
                            result == null -> null
                            // Known failure shapes get said in words — an uninstalled source's
                            // exception carries no message at all, and an extension that crashed
                            // internally carries R8's mangled null-check text. The source's own words
                            // otherwise: a 403 and a timeout are different facts and only the source
                            // knows which happened.
                            result.isFailure -> result.exceptionOrNull().let { e ->
                                RefreshResult.Failed(e?.describeForUser() ?: e?.message)
                            }
                            result.getOrDefault(0) > 0 -> RefreshResult.Found(result.getOrDefault(0))
                            else -> RefreshResult.UpToDate
                        }
                    },
                )
            }
        }
    }

    /** Clears the result once the screen has said it, so it is not repeated on every recomposition. */
    fun refreshResultShown() {
        state.update { it.copy(refreshResult = null) }
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

    /**
     * Queues one item for download, or deletes the file if it is already here.
     *
     * ## Why this page needed it at all
     *
     * It did not have it. The row carried a bookmark and nothing else, on the reasoning that a
     * download button on a thousand-row list is a thousand buttons nobody presses — which is still
     * true of a *button*, and was the wrong conclusion about the *action*. Downloading was
     * reachable only by opening the original screen through *All options*, so the page everybody
     * actually uses could not save anything for a flight.
     *
     * A long press is where the library grid already keeps its per-item actions, so it is where
     * this goes rather than on the row.
     *
     * ## Torrents included
     *
     * A Stremio episode whose only stream is a magnet downloads through TorrServer, which the
     * downloader has always known how to do and nothing had ever asked it to. Nothing here is
     * special-cased for it: the video is whatever the source hands over, and the downloader routes
     * a magnet the same way the player does.
     */
    fun toggleDownload(item: EntryItem) {
        viewModelScope.launchNonCancellable {
            when (contentType) {
                ContentType.MANGA -> {
                    val manga = getManga.await(entryId) ?: return@launchNonCancellable
                    val chapter = getChapter.await(item.id) ?: return@launchNonCancellable
                    if (item.downloaded) {
                        downloadManager.deleteChapters(
                            listOf(chapter),
                            manga,
                            sourceManager.getOrStub(manga.source),
                        )
                    } else {
                        downloadManager.downloadChapters(manga, listOf(chapter))
                    }
                }
                ContentType.ANIME -> {
                    val anime = getAnime.await(entryId) ?: return@launchNonCancellable
                    val episode = getEpisode.await(item.id) ?: return@launchNonCancellable
                    if (item.downloaded) {
                        animeDownloadManager.deleteEpisodes(
                            listOf(episode),
                            anime,
                            animeSourceManager.getOrStub(anime.source),
                        )
                    } else {
                        animeDownloadManager.downloadEpisodes(anime, listOf(episode))
                    }
                }
            }
        }
    }

    /**
     * Whole days from now until [epochMillis], never negative, null when there is nothing to count to.
     *
     * Local days rather than 24-hour blocks: "in 1 day" should mean tomorrow, which is what somebody
     * reads it as, and what Mihon's own countdown means on its screen.
     */
    private fun daysUntil(epochMillis: Long?): Int? {
        if (epochMillis == null || epochMillis <= 0L) return null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val target = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, target).toInt().coerceAtLeast(0)
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
