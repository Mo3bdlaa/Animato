package animato.app.navigation

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.app.discover.AiringItem
import animato.app.discover.MetadataCatalog
import animato.app.updates.UpdateItem
import animato.app.updates.toUpdateItem
import animato.domain.content.ContentPreferences
import animato.domain.content.ContentType
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.interactor.GetUpdates
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import java.time.Instant as JavaInstant

/**
 * One entry the user was part-way through, whichever library it came from.
 *
 * [coverData] is whatever the image loader was already given for that library — Mihon's `MangaCover`
 * or the anime `AnimeCover` — passed through rather than converted, because both already have a
 * fetcher registered and neither needs to know about the other to be drawn.
 */
@Immutable
data class ContinueItem(
    val entryId: Long,
    val contentType: ContentType,
    val title: String,
    val itemNumber: Double,
    val lastViewedAt: Long,
    val coverData: Any?,
) {
    /** The identity a dismissal is keyed on: the entry, within its half. */
    val railKey: String get() = "${contentType.name}:$entryId"
}

@Immutable
data class HomeScreenState(
    val isLoading: Boolean = true,
    val continueItems: List<ContinueItem> = emptyList(),
    val updateItems: List<UpdateItem> = emptyList(),
    /**
     * Library anime with an episode still to come this week.
     *
     * Its own field rather than part of the combine below, because it is the one thing on this
     * screen that comes off the network. Home must draw instantly from two local databases; a rail
     * that waits on AniList arrives when it arrives, and until then simply is not there.
     */
    val airingItems: List<AiringItem> = emptyList(),
)

/**
 * What the home screen shows: where you left off, and what arrived since.
 *
 * The two histories are separate databases with no join between them, so the merge happens here, in
 * memory, on a list that is already bounded — history is capped at what a person has actually
 * opened, and only the most recent handful is ever drawn.
 */
class HomeScreenModel(
    getHistory: GetHistory = Injekt.get(),
    getAnimeHistory: GetAnimeHistory = Injekt.get(),
    getUpdates: GetUpdates = Injekt.get(),
    getAnimeUpdates: GetAnimeUpdates = Injekt.get(),
    private val contentPreferences: ContentPreferences = Injekt.get(),
    downloadManager: DownloadManager = Injekt.get(),
    animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val metadataCatalog: MetadataCatalog = MetadataCatalog(),
) : ViewModel() {

    /**
     * How many items are queued right now, across both halves.
     *
     * Its own flow rather than a field on the main state: the queue ticks while a download runs,
     * and folding that into the state that carries Continue and Updates would rebuild both lists
     * every few hundred milliseconds for a number.
     */
    val queuedDownloads: StateFlow<Int> = combine(
        downloadManager.queueState,
        animeDownloadManager.queueState,
    ) { manga, anime -> manga.size + anime.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val state: StateFlow<HomeScreenState>
        field = MutableStateFlow<HomeScreenState>(HomeScreenState())

    init {
        // Everything in the last month. Home shows a handful; asking for less than a window means
        // asking again the moment the handful is all read, and asking for all of it means paging a
        // year of rows to draw five.
        //
        // The two interactors agree on neither the signature nor the type. Mihon's takes the
        // filters its own Updates screen needs and a `kotlin.time.Instant`; the anime one takes
        // neither the filters nor that type, because it was ported before Mihon migrated. So the
        // moment is computed once and handed to each in its own currency, and the filters are
        // passed as their no-op values rather than pretending the calls are symmetric. Nothing is
        // excluded on purpose: this is a summary of what arrived, not a filtered feed.
        val since = Clock.System.now().minus(UPDATE_WINDOW)
        val sinceLegacy: JavaInstant = JavaInstant.ofEpochMilli(since.toEpochMilliseconds())

        combine(
            getHistory.subscribe(""),
            getAnimeHistory.subscribe(""),
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
            contentPreferences.hiddenFromContinue.changes(),
        ) { mangaHistory, animeHistory, mangaUpdates, animeUpdates, hidden ->
            val dismissedAt = hidden.mapNotNull(::parseDismissal).toMap()
            val continueItems = buildList {
                mangaHistory.forEach {
                    add(
                        ContinueItem(
                            entryId = it.mangaId,
                            contentType = ContentType.MANGA,
                            title = it.title,
                            itemNumber = it.chapterNumber,
                            lastViewedAt = it.readAt?.time ?: 0L,
                            coverData = it.coverData,
                        ),
                    )
                }
                animeHistory.forEach {
                    add(
                        ContinueItem(
                            entryId = it.animeId,
                            contentType = ContentType.ANIME,
                            title = it.title,
                            itemNumber = it.episodeNumber,
                            lastViewedAt = it.seenAt?.time ?: 0L,
                            coverData = it.coverData,
                        ),
                    )
                }
            }
                .sortedByDescending { it.lastViewedAt }
                .distinctBy { it.contentType to it.entryId }
                // A dismissal outranks the history that is older than it and nothing newer: hide
                // was asked for from a device with exactly that shape — "off this rail, but it
                // can stay in the library" — and opening the entry again is the change of mind
                // that puts it back, because that writes a fresher history row.
                .filterNot { (dismissedAt[it.railKey] ?: Long.MIN_VALUE) >= it.lastViewedAt }
                .take(CONTINUE_LIMIT)

            // The same row object the Updates feed draws — Home is the first five of it, not a
            // second thing that happens to look similar.
            val updateItems = (mangaUpdates.map { it.toUpdateItem() } + animeUpdates.map { it.toUpdateItem() })
                .sortedByDescending { it.fetchedAt }
                .take(UPDATE_LIMIT)

            HomeScreenState(
                isLoading = false,
                continueItems = continueItems,
                updateItems = updateItems,
            )
        }
            // Copied onto the existing state rather than replacing it, so a rail that has already
            // come back from the network is not wiped by the next local emission.
            .onEach { newState -> state.value = newState.copy(airingItems = state.value.airingItems) }
            .launchIn(viewModelScope)

        loadAiring()
    }

    /**
     * The same refresh Updates runs: ask both libraries for anything new, per the lens.
     *
     * Each job refuses if it is already running and says so by returning false; started means
     * either half accepted.
     */
    fun refresh(): Boolean {
        val context = Injekt.get<Application>()
        val lens = contentPreferences.contentFilter.get()
        val manga = lens.includesManga && LibraryUpdateJob.startNow(context)
        val anime = lens.includesAnime && AnimeLibraryUpdateJob.startNow(context)
        return manga || anime
    }

    /**
     * Ask what is airing, once per opening of the app.
     *
     * Seeded from the most recently added anime, which is the best available guess at what is
     * currently airing without asking about a whole library — a finished series from four years
     * ago has no next episode and costs a field in the request to say so.
     *
     * Anything already out is dropped along with anything further off than a week: the rail
     * answers *what is coming*, and a countdown of nineteen days is a fact rather than a plan.
     */
    private fun loadAiring() {
        viewModelScope.launch {
            val titles = runCatching { getLibraryAnime.await().map { it.anime.title } }
                .getOrDefault(emptyList())
                .asReversed()
            if (titles.isEmpty()) return@launch

            val now = System.currentTimeMillis()
            val horizon = now + AIRING_WINDOW.inWholeMilliseconds
            val airing = metadataCatalog.airing(titles)
                .filter { it.airingAtMillis in now..horizon }
                .take(AIRING_LIMIT)
            state.value = state.value.copy(airingItems = airing)
        }
    }

    /** Dismisses one entry from the Continue rail, timestamped so a later open un-dismisses it. */
    fun hideFromContinue(item: ContinueItem) {
        val key = "${item.contentType.name}:${item.entryId}"
        val entry = "$key:${System.currentTimeMillis()}"
        contentPreferences.hiddenFromContinue.let { pref ->
            pref.set(pref.get().filterNot { it.startsWith("$key:") }.toSet() + entry)
        }
    }

    companion object {
        private fun parseDismissal(entry: String): Pair<String, Long>? {
            val parts = entry.split(':')
            if (parts.size != 3) return null
            val at = parts[2].toLongOrNull() ?: return null
            return "${parts[0]}:${parts[1]}" to at
        }

        /** The rail is scrolled, not paged; past this many nobody is still looking. */
        private const val CONTINUE_LIMIT = 20

        /** Five rows and a way to see the rest. Home is a summary; Updates is the feed. */
        private const val UPDATE_LIMIT = 5

        private val UPDATE_WINDOW = 30.days

        /** *This week*, taken literally. Past that it is a schedule and belongs on a page. */
        private val AIRING_WINDOW = 7.days

        private const val AIRING_LIMIT = 12
    }
}
