package animato.anime.player

import animato.anime.services.download.EpisodeVideoResolver
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

/**
 * The player's answer to the downloader's question.
 *
 * `:anime:services` declares [EpisodeVideoResolver] because the downloader needs a video and has no
 * business knowing how one is found; this module knows, because finding one is what the player does
 * before it can play anything. Asking each hoster and picking the best stream is the same work in
 * both cases, so it is the same code — reached through an interface rather than an import upwards.
 *
 * Aniyomi had the downloader call [EpisodeLoader] and [HosterLoader] directly, which made a
 * background service depend on the player package.
 */
class PlayerEpisodeVideoResolver : EpisodeVideoResolver {

    override suspend fun resolveBestVideo(episode: Episode, anime: Anime, source: AnimeSource): Video? {
        val hosters = EpisodeLoader.getHosters(episode, anime, source)
        if (hosters.isEmpty()) return null
        return HosterLoader.getBestVideo(source, hosters)
    }
}
