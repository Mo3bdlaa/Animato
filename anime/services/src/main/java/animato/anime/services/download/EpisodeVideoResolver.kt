package animato.anime.services.download

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * Resolves an episode to the single video that should be downloaded for it.
 *
 * The downloader needs this, and the only implementation is in the player, which sits above this
 * module: it asks each hoster in turn and picks the best stream the user's quality preferences
 * allow. Aniyomi expressed that by having the downloader import `EpisodeLoader` and `HosterLoader`
 * from `ui.player.loader` directly, which makes a background service depend on the player.
 *
 * Stating the requirement as an interface here inverts that. The downloader depends on what it
 * needs; the player supplies it and binds it through Injekt. Nothing below this line knows the
 * player exists.
 */
fun interface EpisodeVideoResolver {

    /**
     * @return the best video available for [episode], or null when no hoster yields one.
     */
    suspend fun resolveBestVideo(episode: Episode, anime: Anime, source: AnimeSource): Video?
}
