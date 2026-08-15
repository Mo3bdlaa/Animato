package animato.domain.content.interactor

import animato.domain.content.ContentFilter
import animato.domain.content.LibraryEntry
import animato.domain.content.asLibraryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.manga.interactor.GetLibraryManga

/**
 * Both libraries as one list.
 *
 * The two are separate databases with no join between them, so the merge is in memory. That is
 * affordable because a library is bounded by what one person has chosen to keep — thousands, not
 * millions — and it avoids the alternative, which is a schema that both halves have to agree on
 * forever.
 *
 * [ContentFilter] decides which halves are read, and a filtered-out half is never subscribed to:
 * someone who only watches anime does no manga work at all, rather than doing it and discarding
 * the result.
 */
class GetUnifiedLibrary(
    private val getLibraryManga: GetLibraryManga,
    private val getLibraryAnime: GetLibraryAnime,
) {

    fun subscribe(filter: ContentFilter = ContentFilter.ALL): Flow<List<LibraryEntry>> {
        val manga: Flow<List<LibraryEntry>> = if (filter.includesManga) {
            getLibraryManga.subscribe().map { library -> library.map { it.asLibraryEntry() } }
        } else {
            flowOf(emptyList())
        }
        val anime: Flow<List<LibraryEntry>> = if (filter.includesAnime) {
            getLibraryAnime.subscribe()
        } else {
            flowOf(emptyList())
        }
        return combine(manga, anime) { mangaEntries, animeEntries -> mangaEntries + animeEntries }
    }

    suspend fun await(filter: ContentFilter = ContentFilter.ALL): List<LibraryEntry> {
        val manga = if (filter.includesManga) getLibraryManga.await().map { it.asLibraryEntry() } else emptyList()
        val anime = if (filter.includesAnime) getLibraryAnime.await() else emptyList()
        return manga + anime
    }
}
