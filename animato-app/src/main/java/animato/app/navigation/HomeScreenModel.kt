package animato.app.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import animato.domain.content.ContentType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mihon.core.viewmodel.StateViewModel
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.interactor.GetLibraryManga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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

@Immutable
data class HomeScreenState(
    val isLoading: Boolean = true,
    val continueItems: List<ContinueItem> = emptyList(),
    val mangaCount: Int = 0,
    val animeCount: Int = 0,
)

/**
 * What the home screen shows: where you left off, and how much you have.
 *
 * The two histories are separate databases with no join between them, so the merge happens here, in
 * memory, on a list that is already bounded — history is capped at what a person has actually
 * opened, and only the most recent handful is ever drawn.
 */
class HomeScreenModel(
    getHistory: GetHistory = Injekt.get(),
    getAnimeHistory: GetAnimeHistory = Injekt.get(),
    getLibraryManga: GetLibraryManga = Injekt.get(),
    getLibraryAnime: GetLibraryAnime = Injekt.get(),
) : StateViewModel<HomeScreenState>(HomeScreenState()) {

    init {
        combine(
            getHistory.subscribe(""),
            getAnimeHistory.subscribe(""),
            getLibraryManga.subscribe(),
            getLibraryAnime.subscribe(),
        ) { mangaHistory, animeHistory, manga, anime ->
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

            HomeScreenState(
                isLoading = false,
                continueItems = continueItems,
                mangaCount = manga.size,
                animeCount = anime.size,
            )
        }
            .onEach { newState -> mutableState.value = newState }
            .launchIn(viewModelScope)
    }

    companion object {
        /** The rail is scrolled, not paged; past this many nobody is still looking. */
        private const val CONTINUE_LIMIT = 20
    }
}
