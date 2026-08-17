package animato.app.updates

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentFilter
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.interactor.GetUpdates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import java.time.Instant as JavaInstant

/** One day of the feed. The date is the divider; the rows are the content. */
@Immutable
data class UpdateDay(
    val date: LocalDate,
    val items: List<UpdateItem>,
)

@Immutable
data class UpdatesState(
    val isLoading: Boolean = true,
    val lens: ContentFilter = ContentFilter.ALL,
    val days: List<UpdateDay> = emptyList(),
    val isRefreshing: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && days.isEmpty()
}

/**
 * What arrived, both halves, grouped by the day it arrived.
 *
 * ## Why this replaces two screens
 *
 * Updates used to be Mihon's feed or the anime feed depending on the lens — two lists that could
 * never be seen together, so a day on which one chapter and one episode arrived was two screens
 * with one row each. A feed is the one place where merging is not a nicety: *what happened
 * yesterday* is a single question.
 *
 * ## Days rather than a flat list
 *
 * The date is a divider and not a section title — 13 Medium muted, per the design sheet — because a
 * feed with five 18-point headings reads as five articles with rows attached to them. Grouping is
 * done here rather than in the composable so that the header rows are real list items with stable
 * keys and can be made sticky.
 *
 * ## What a row can do
 *
 * Opening one launches the reader or the player for that exact chapter or episode, which is what
 * both halves' own feeds did and is the only reason to tap a row in a feed. Downloading one is the
 * other action, and it needs the entry and the item themselves rather than their ids, so both are
 * fetched on the tap — off the main thread, and in a scope that survives leaving the screen, since
 * a download you asked for should not be cancelled by walking away from the list you asked from.
 */
class UpdatesScreenModel(
    getUpdates: GetUpdates = Injekt.get(),
    getAnimeUpdates: GetAnimeUpdates = Injekt.get(),
    contentPreferences: ContentPreferences = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val setReadStatus: SetReadStatus = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<UpdatesState>
        field = MutableStateFlow(UpdatesState(lens = contentPreferences.contentFilter.get()))

    init {
        // The two interactors agree on neither the signature nor the type: Mihon's takes the
        // filters its own screen needs and a kotlin.time.Instant, the anime one takes neither
        // because it was ported before Mihon migrated. So the moment is computed once and handed to
        // each in its own currency. Nothing is excluded on purpose — this is what arrived.
        val since = Clock.System.now().minus(FEED_WINDOW)
        val sinceLegacy: JavaInstant = JavaInstant.ofEpochMilli(since.toEpochMilliseconds())

        combine(
            getUpdates.subscribe(
                instant = since,
                unread = null,
                started = null,
                bookmarked = null,
                hideExcludedScanlators = false,
                includedCategories = emptyList(),
                excludedCategories = emptyList(),
            ),
            getAnimeUpdates.subscribe(instant = sinceLegacy),
            contentPreferences.contentFilter.changes(),
        ) { mangaUpdates, animeUpdates, lens ->
            val items = buildList {
                if (lens.includesManga) addAll(mangaUpdates.map { it.toUpdateItem() })
                if (lens.includesAnime) addAll(animeUpdates.map { it.toUpdateItem() })
            }
                .sortedByDescending { it.fetchedAt }
                // Only what is still unopened. The feed used to keep read rows around as a log,
                // and a device asked for the other behaviour: marking something opened is how a
                // row is dismissed, so the feed is a to-do list rather than a diary.
                .filter { it.isNew }

            UpdatesState(isLoading = false, lens = lens, days = items.groupIntoDays())
        }
            .onEach { newState -> state.value = newState.copy(isRefreshing = state.value.isRefreshing) }
            .launchIn(viewModelScope)
    }

    /**
     * Ask both libraries for new items.
     *
     * Each job refuses if it is already running and says so by returning false; refreshing is
     * reported as started when either accepted, because one half already working is still the
     * screen doing what was asked.
     */
    fun refresh(): Boolean {
        val context = Injekt.get<Application>()
        val manga = state.value.lens.includesManga && LibraryUpdateJob.startNow(context)
        val anime = state.value.lens.includesAnime && AnimeLibraryUpdateJob.startNow(context)
        return manga || anime
    }

    /**
     * Marks a row opened, or puts it back.
     *
     * Both halves take the item itself rather than its id — they write progress and a history row
     * beside the flag — so this fetches it first, the same way the title page does.
     */
    fun toggleViewed(item: UpdateItem) {
        viewModelScope.launchNonCancellable {
            when (item.contentType) {
                ContentType.MANGA ->
                    getChapter.await(item.itemId)?.let { setReadStatus.await(item.isNew, it) }
                ContentType.ANIME ->
                    getEpisode.await(item.itemId)?.let { setSeenStatus.await(item.isNew, it) }
            }
        }
    }

    /**
     * Queue this row's chapter or episode.
     *
     * A source that is not installed is skipped rather than reported: the row is still listed
     * because the item is in the database, but nothing can fetch it, and a download that silently
     * never starts is worse than one that never queues.
     */
    fun download(item: UpdateItem) {
        viewModelScope.launchNonCancellable {
            when (item.contentType) {
                ContentType.MANGA -> {
                    val manga = getManga.await(item.entryId) ?: return@launchNonCancellable
                    sourceManager.get(manga.source) ?: return@launchNonCancellable
                    val chapter = getChapter.await(item.itemId) ?: return@launchNonCancellable
                    downloadManager.downloadChapters(manga, listOf(chapter))
                }
                ContentType.ANIME -> {
                    val anime = getAnime.await(item.entryId) ?: return@launchNonCancellable
                    animeSourceManager.get(anime.source) ?: return@launchNonCancellable
                    val episode = getEpisode.await(item.itemId) ?: return@launchNonCancellable
                    animeDownloadManager.downloadEpisodes(anime, listOf(episode))
                }
            }
        }
    }

    private companion object {
        /**
         * How far back the feed goes.
         *
         * Longer than Home's window, which shows five rows and links here. Ninety days is about the
         * point past which "what arrived" stops being a feed and starts being an archive, and it
         * bounds the query without paging — both halves return rows already joined to their entry,
         * so the cost is in rows and not in round trips.
         */
        val FEED_WINDOW = 90.days
    }
}

private fun List<UpdateItem>.groupIntoDays(): List<UpdateDay> {
    val zone = ZoneId.systemDefault()
    return groupBy { Instant.ofEpochMilli(it.fetchedAt).atZone(zone).toLocalDate() }
        .map { (date, items) -> UpdateDay(date, items) }
        .sortedByDescending { it.date }
}
