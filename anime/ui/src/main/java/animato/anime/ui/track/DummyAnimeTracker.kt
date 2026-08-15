package animato.anime.ui.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.test.DummyTracker
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack as DbAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack

/**
 * A tracker for previews that answers about anime.
 *
 * Mihon has `DummyTracker` and it stops at [eu.kanade.tachiyomi.data.track.Tracker], so a preview
 * of an anime tracking sheet cannot use it. Delegating to one covers the whole tracker half in a
 * line and leaves only the anime answers to give.
 */
data class DummyAnimeTracker(
    private val delegate: DummyTracker,
    val valStatuses: List<Long> = (1L..6L).toList(),
    val valWatchingStatus: Long = 1L,
    val valRewatchingStatus: Long = 1L,
    val valCompletionStatus: Long = 2L,
    val valScoreList: List<String> = (0..10).map(Int::toString),
    val val10PointScore: Double = 5.4,
    val valSearchResults: List<AnimeTrackSearch> = emptyList(),
) : eu.kanade.tachiyomi.data.track.Tracker by delegate, AnimeTracker {

    override fun getCompletionStatus(): Long = valCompletionStatus

    override fun getScoreList(): ImmutableList<String> = valScoreList.toImmutableList()

    override fun indexToScore(index: Int): Double = valScoreList[index].toDouble()

    override fun getStatusListAnime(): List<Long> = valStatuses

    override fun getWatchingStatus(): Long = valWatchingStatus

    override fun getRewatchingStatus(): Long = valRewatchingStatus

    override fun get10PointScore(track: DomainAnimeTrack): Double = val10PointScore

    override fun displayScore(track: DomainAnimeTrack): String = track.score.toString()

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        1L -> AYMR.strings.watching
        2L -> AYMR.strings.plan_to_watch
        3L -> MR.strings.completed
        4L -> MR.strings.on_hold
        5L -> MR.strings.dropped
        6L -> AYMR.strings.repeating_anime
        else -> null
    }

    override suspend fun update(track: DbAnimeTrack, didWatchEpisode: Boolean): DbAnimeTrack = track

    override suspend fun bind(track: DbAnimeTrack, hasSeenEpisodes: Boolean): DbAnimeTrack = track

    override suspend fun searchAnime(query: String): List<AnimeTrackSearch> = valSearchResults

    override suspend fun refresh(track: DbAnimeTrack): DbAnimeTrack = track
}
