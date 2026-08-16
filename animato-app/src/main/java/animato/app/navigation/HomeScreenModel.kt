package animato.app.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
)

/**
 * Something that arrived since you last looked, whichever library it came from.
 *
 * [isNew] is unread or unseen — the pill on the row. It is the state that makes the section worth
 * having: a feed of things you have already dealt with is a log, not news.
 */
@Immutable
data class UpdateItem(
    val entryId: Long,
    val contentType: ContentType,
    val title: String,
    val itemName: String,
    val fetchedAt: Long,
    val isNew: Boolean,
    val coverData: Any?,
)

@Immutable
data class HomeScreenState(
    val isLoading: Boolean = true,
    val continueItems: List<ContinueItem> = emptyList(),
    val updateItems: List<UpdateItem> = emptyList(),
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
) : ViewModel() {

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
        ) { mangaHistory, animeHistory, mangaUpdates, animeUpdates ->
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
                .take(CONTINUE_LIMIT)

            val updateItems = buildList {
                mangaUpdates.forEach {
                    add(
                        UpdateItem(
                            entryId = it.mangaId,
                            contentType = ContentType.MANGA,
                            title = it.mangaTitle,
                            itemName = it.chapterName,
                            fetchedAt = it.dateFetch,
                            isNew = !it.read,
                            coverData = it.coverData,
                        ),
                    )
                }
                animeUpdates.forEach {
                    add(
                        UpdateItem(
                            entryId = it.animeId,
                            contentType = ContentType.ANIME,
                            title = it.animeTitle,
                            itemName = it.episodeName,
                            fetchedAt = it.dateFetch,
                            isNew = !it.seen,
                            coverData = it.coverData,
                        ),
                    )
                }
            }
                .sortedByDescending { it.fetchedAt }
                .take(UPDATE_LIMIT)

            HomeScreenState(
                isLoading = false,
                continueItems = continueItems,
                updateItems = updateItems,
            )
        }
            .onEach { newState -> state.value = newState }
            .launchIn(viewModelScope)
    }

    companion object {
        /** The rail is scrolled, not paged; past this many nobody is still looking. */
        private const val CONTINUE_LIMIT = 20

        /** Five rows and a way to see the rest. Home is a summary; Updates is the feed. */
        private const val UPDATE_LIMIT = 5

        private val UPDATE_WINDOW = 30.days
    }
}
