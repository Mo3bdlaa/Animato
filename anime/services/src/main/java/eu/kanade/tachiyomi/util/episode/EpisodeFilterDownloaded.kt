package eu.kanade.tachiyomi.util.episode

import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Returns a copy of the list with the episodes that are not downloaded removed.
 *
 * Lives with the download cache it asks, because both the player and the episode list need it and
 * a file cannot be in two modules — Kotlin will compile a copy in each without complaint, and the
 * duplicate only surfaces when the two dex outputs are merged.
 */
fun List<Episode>.filterDownloadedEpisodes(anime: Anime): List<Episode> {
    if (anime.isLocal()) return this

    val downloadCache: AnimeDownloadCache = Injekt.get()

    return filter {
        downloadCache.isEpisodeDownloaded(
            it.name,
            it.scanlator,
            anime.title,
            anime.source,
            false,
        )
    }
}
