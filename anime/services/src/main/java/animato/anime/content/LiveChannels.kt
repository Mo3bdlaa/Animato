package animato.anime.content

import animato.anime.di.AnimatoScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * What it takes to play a live channel without opening its page first.
 *
 * A channel has one thing to play and it is on now, so its title page is a step with nothing on it
 * but a button. Opening a channel goes straight to the player, and the player steps to the next
 * channel and the previous one; both need the same thing — the episode row to hand the player —
 * and a channel that has never been opened has no episode row yet.
 */
@SingleIn(AnimatoScope::class)
@Inject
class LiveChannels(
    private val sourceManager: AnimeSourceManager,
    private val getAnime: GetAnime,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
) {

    /** Whether the entry is a live channel, and so opens in the player rather than on a page. */
    fun isLive(anime: Anime): Boolean = sourceManager.get(anime.source).entryForm(anime.url) == EntryForm.Live

    /**
     * The episode to play for [animeId], fetching the channel's one row if it has never been opened.
     * Null when it cannot be played — the source is gone, or offers nothing.
     */
    suspend fun episodeToPlay(animeId: Long): Long? {
        val anime = getAnime.await(animeId) ?: return null
        getEpisodesByAnimeId.await(anime.id).firstOrNull()?.let { return it.id }
        val source = sourceManager.get(anime.source) ?: return null
        val fetched = runCatching { source.getEpisodeList(anime.toSAnime()) }.getOrNull() ?: return null
        return runCatching { syncEpisodesWithSource.await(fetched, anime, source) }.getOrNull()
            ?.firstOrNull()?.id
            ?: getEpisodesByAnimeId.await(anime.id).firstOrNull()?.id
    }

    /**
     * The channel next to [anime] in its source's lineup, as the entry and episode to open.
     * Null when the source has no lineup or there is no other channel in it.
     */
    suspend fun adjacent(anime: Anime, forward: Boolean): Pair<Long, Long>? {
        val source = sourceManager.get(anime.source)
        val lineup = source as? ChannelLineup ?: return null
        val next = lineup.adjacentChannel(anime.url, forward) ?: return null
        val local = networkToLocalAnime.await(next.toDomainAnime(source.id))
        val episodeId = episodeToPlay(local.id) ?: return null
        return local.id to episodeId
    }

    /** Whether [anime]'s source can step to another channel at all. */
    fun hasLineup(anime: Anime): Boolean = sourceManager.get(anime.source) is ChannelLineup
}
