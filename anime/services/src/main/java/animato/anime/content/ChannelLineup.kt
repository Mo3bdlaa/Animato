package animato.anime.content

import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * A source whose live channels come in an order, so the player can step through them.
 *
 * Channel up and down is how a television is watched: you do not go back to a grid to see what is
 * on the next channel. A source that has a lineup says what comes after a channel and before it;
 * the player turns that into next and previous.
 */
interface ChannelLineup {
    /**
     * The live channel after [entryUrl] — or before it, when [forward] is false — in the source's
     * own order, wrapping round at either end. Null when there is no other channel to go to.
     */
    suspend fun adjacentChannel(entryUrl: String, forward: Boolean): SAnime?
}
