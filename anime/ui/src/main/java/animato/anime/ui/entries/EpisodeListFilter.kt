package animato.anime.ui.entries

import eu.kanade.domain.entries.anime.model.downloadedFilter
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.manga.model.applyFilter
import tachiyomi.source.local.entries.anime.isLocal

/**
 * The episode filters, applied to what the details screen is about to draw.
 *
 * The same filters as the `List<Episode>` overload in `:anime:services`, but over [EpisodeList.Item]
 * — which already knows whether an episode is downloaded, so this needs no download manager. It
 * lives here rather than beside that overload because [EpisodeList] is a screen model, and a
 * services-layer helper taking a UI type would invert the layering.
 */
fun List<EpisodeList.Item>.applyFilters(anime: Anime): Sequence<EpisodeList.Item> {
    val isLocalAnime = anime.isLocal()
    val unseenFilter = anime.unseenFilter
    val downloadedFilter = anime.downloadedFilter
    val bookmarkedFilter = anime.bookmarkedFilter
    val fillermarkedFilter = anime.fillermarkedFilter
    return asSequence()
        .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
        .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
        .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
        .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
        .sortedWith { (episode1), (episode2) -> getEpisodeSort(anime).invoke(episode1, episode2) }
}
