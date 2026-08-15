package animato.anime.track

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.domain.track.model.Track as DomainTrack

/**
 * A tracker that exists only on the anime side.
 *
 * The five in [AnimeTrackerManager] wrap a Mihon tracker of the same name and delegate everything
 * about *being* a tracker to it. Simkl and Jellyfin have no manga counterpart to delegate to — no
 * Mihon tracker has their id, their logo or their credentials — so they are `BaseTracker`s in their
 * own right, and that means answering Mihon's `Tracker` interface in full, manga half included.
 *
 * That half is answered here, once, by refusing. It is not a stub standing in for work not yet
 * done: Simkl tracks shows and films, Jellyfin indexes a media server, and neither has a concept of
 * a chapter to expose. A tracker with no manga API cannot have a manga method that means anything,
 * and returning an empty list from one would be a quieter lie than throwing.
 *
 * Nothing reaches them. Mihon builds its manga tracking list from its own `TrackerManager`, which
 * is final and does not know these exist; the anime side reaches them through [AnimeTrackerManager]
 * and the `AnimeTracker` interface. So [notAMangaTracker] marks code that is unreachable by
 * construction, and says why rather than pretending otherwise.
 */
abstract class AnimeOnlyTracker(id: Long, name: String) : BaseTracker(id, name), AnimeTracker {

    private fun notAMangaTracker(): Nothing =
        throw UnsupportedOperationException("$name tracks anime only")

    /**
     * `BaseTracker` and `AnimeTracker` both supply one, identically, and Kotlin will not pick.
     */
    override fun indexToScore(index: Int): Double = index.toDouble()

    final override fun getStatusList(): List<Long> = getStatusListAnime()

    final override fun getStatus(status: Long): StringResource? = getStatusForAnime(status)

    final override fun getReadingStatus(): Long = getWatchingStatus()

    final override fun getRereadingStatus(): Long = getRewatchingStatus()

    final override fun displayScore(track: DomainTrack): String = notAMangaTracker()

    final override suspend fun update(track: Track, didReadChapter: Boolean): Track = notAMangaTracker()

    final override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = notAMangaTracker()

    final override suspend fun search(query: String): List<TrackSearch> = notAMangaTracker()

    final override suspend fun refresh(track: Track): Track = notAMangaTracker()
}
