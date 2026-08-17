package animato.app.coil

import coil3.key.Keyer
import coil3.request.Options
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import tachiyomi.domain.entries.anime.model.AnimeCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.entries.anime.model.Anime as DomainAnime

/**
 * What an anime cover is called in the caches.
 *
 * A `Keyer` is half of what makes a type loadable at all — without one Coil has no name to file the
 * result under, and [AnimeCoverFetcher] explains what the absence of the pair looked like on a
 * device. The key includes `coverLastModified` so that refreshing an entry whose cover changed
 * actually shows the new one instead of the cached old one.
 *
 * `anime;` prefixes the key. The two halves share one disk cache and an anime and a manga can be
 * the same work from the same site with the same cover URL — without the prefix they would collide,
 * and a cover fetched with one source's headers would be served for the other.
 */
class AnimeKeyer : Keyer<DomainAnime> {
    override fun key(data: DomainAnime, options: Options): String {
        return if (data.hasCustomCover()) {
            "anime;${data.id};${data.coverLastModified}"
        } else {
            "anime;${data.thumbnailUrl};${data.coverLastModified}"
        }
    }
}

class AnimeCoverKeyer(
    private val coverCache: AnimeCoverCache = Injekt.get(),
) : Keyer<AnimeCover> {
    override fun key(data: AnimeCover, options: Options): String {
        return if (coverCache.getCustomCoverFile(data.animeId).exists()) {
            "anime;${data.animeId};${data.lastModified}"
        } else {
            "anime;${data.url};${data.lastModified}"
        }
    }
}
