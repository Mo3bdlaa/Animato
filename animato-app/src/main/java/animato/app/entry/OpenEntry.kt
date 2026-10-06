package animato.app.entry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import animato.anime.content.LiveChannels
import animato.anime.player.PlayerLauncher
import animato.domain.content.ContentType
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.interactor.GetAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Opens an entry the way its kind wants to be opened: a live channel in the player, everything else
 * on its title page.
 *
 * A channel's page has one button on it and nothing else — there is no description worth reading,
 * no episode list, no progress — so tapping a channel plays it, the way a channel list works on any
 * television. Once playing, next and previous step through the channels around it (see
 * [LiveChannels]). Films and series, including the ones a playlist carries, open on their page as
 * they always have.
 *
 * One function for every place an entry is tapped, so a channel behaves the same from the library,
 * search, Continue and a source's own grid.
 */
@Composable
fun rememberOpenEntry(): (entryId: Long, contentType: ContentType, fromSource: Boolean) -> Unit {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(navigator, context, scope) {
        { entryId, contentType, fromSource ->
            scope.launch {
                val liveEpisode = if (contentType ==
                    ContentType.ANIME
                ) {
                    withIOContext { liveEpisodeFor(entryId) }
                } else {
                    null
                }
                if (liveEpisode != null) {
                    PlayerLauncher.startPlayerActivity(
                        context = context,
                        animeId = entryId,
                        episodeId = liveEpisode,
                        extPlayer = false,
                    )
                } else {
                    navigator.push(EntryScreen(entryId, contentType, fromSource = fromSource))
                }
            }
        }
    }
}

private suspend fun liveEpisodeFor(entryId: Long): Long? {
    val liveChannels = Injekt.get<LiveChannels>()
    val anime = Injekt.get<GetAnime>().await(entryId) ?: return null
    if (!liveChannels.isLive(anime)) return null
    return liveChannels.episodeToPlay(entryId)
}
