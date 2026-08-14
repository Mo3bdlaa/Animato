package animato.domain.content.interactor

import animato.domain.content.ContentFilter
import animato.domain.content.LibraryEntry
import animato.domain.content.asLibraryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga

/**
 * Reads the anime and manga libraries as a single list.
 *
 * The two live in separate databases, so this is a merge in memory rather than a query — there is
 * no join to reach for. Library-sized collections make that cheap, and it keeps both databases
 * untouched, which is what lets the manga half stay mergeable with upstream.
 *
 * A filtered-out side is never subscribed to at all, so browsing anime only does no manga work.
 */
class GetUnifiedLibrary(
    private val getLibraryAnime: GetLibraryAnime,
    private val getLibraryManga: GetLibraryManga,
) {

    fun subscribe(filter: ContentFilter = ContentFilter.ALL): Flow<List<LibraryEntry>> {
        val animeFlow: Flow<List<LibraryEntry>> = if (filter.includesAnime) {
            getLibraryAnime.subscribe()
        } else {
            flowOf(emptyList())
        }

        val mangaFlow: Flow<List<LibraryEntry>> = if (filter.includesManga) {
            getLibraryManga.subscribe().map { library -> library.map { it.asLibraryEntry() } }
        } else {
            flowOf(emptyList())
        }

        return combine(animeFlow, mangaFlow) { anime, manga -> anime + manga }
    }

    suspend fun await(filter: ContentFilter = ContentFilter.ALL): List<LibraryEntry> {
        val anime = if (filter.includesAnime) getLibraryAnime.await() else emptyList()
        val manga = if (filter.includesManga) {
            getLibraryManga.await().map { it.asLibraryEntry() }
        } else {
            emptyList()
        }
        return anime + manga
    }
}
