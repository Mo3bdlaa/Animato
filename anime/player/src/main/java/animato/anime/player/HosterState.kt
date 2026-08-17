package animato.anime.player

import eu.kanade.tachiyomi.animesource.model.Video

/**
 * What is known about one hoster for an episode.
 *
 * A source can offer the same episode through several hosters, each of which has to be asked
 * separately for its video list. This is the state of that asking, per hoster.
 *
 * Aniyomi declared it inside `QualitySheet.kt`, a Compose file, which meant the loaders — and
 * through them the downloader — imported a screen to get at a model. It carries no Compose and
 * describes no drawing, so it lives on its own here.
 */
sealed class HosterState(open val name: String) {
    data class Idle(override val name: String) : HosterState(name)
    data class Loading(override val name: String) : HosterState(name)

    /**
     * [reason] is the message of whatever the fetch actually threw. Without it every failure
     * between the source and the screen collapses into the word "Error" — and a device report of
     * *"videos load for a while, then say there are none"* is undiagnosable, because the one
     * string that said why was caught and discarded on the way past.
     */
    data class Error(override val name: String, val reason: String? = null) : HosterState(name)
    data class Ready(
        override val name: String,
        val videoList: List<Video>,
        val videoState: List<Video.State>,
    ) : HosterState(name)
}

/**
 * Replaces the video and state at [index], leaving the rest of the list as it was.
 */
fun HosterState.Ready.getChangedAt(index: Int, newVideo: Video, newState: Video.State): HosterState.Ready {
    return HosterState.Ready(
        name = this.name,
        videoList = this.videoList.mapIndexed { idx, video ->
            if (idx == index) newVideo else video
        },
        videoState = this.videoState.mapIndexed { idx, state ->
            if (idx == index) newState else state
        },
    )
}
