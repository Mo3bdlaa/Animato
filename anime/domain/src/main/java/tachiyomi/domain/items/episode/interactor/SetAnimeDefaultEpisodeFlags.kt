package tachiyomi.domain.items.episode.interactor

import aniyomi.domain.library.service.AnimeLibraryPreferences
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.model.Anime

@Inject
class SetAnimeDefaultEpisodeFlags(
    private val libraryPreferences: AnimeLibraryPreferences,
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags,
    private val getFavorites: GetAnimeFavorites,
) {

    suspend fun await(anime: Anime) {
        withContext(NonCancellable) {
            with(libraryPreferences) {
                setAnimeEpisodeFlags.awaitSetAllFlags(
                    animeId = anime.id,
                    unseenFilter = filterEpisodeBySeen().get(),
                    downloadedFilter = filterEpisodeByDownloaded().get(),
                    bookmarkedFilter = filterEpisodeByBookmarked().get(),
                    fillermarkedFilter = filterEpisodeByFillermarked().get(),
                    sortingMode = sortEpisodeBySourceOrNumber().get(),
                    sortingDirection = sortEpisodeByAscendingOrDescending().get(),
                    displayMode = displayEpisodeByNameOrNumber().get(),
                    showPreviews = showEpisodeThumbnailPreviews().get(),
                    showSummaries = showEpisodeSummaries().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withContext(NonCancellable) {
            getFavorites.await().forEach { await(it) }
        }
    }
}
