package eu.kanade.domain.track.anime.interactor

import aniyomi.domain.track.service.AnimeTrackPreferences
import eu.kanade.domain.track.anime.model.toDbTrack
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.toEpisodeUpdate
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import kotlin.math.max

/**
 * Settles the library and a tracker on the same position, in both directions.
 *
 * Tracking used to be one-way for all but one tracker: this returned immediately unless the tracker
 * was an [EnhancedAnimeTracker], which only Jellyfin is. So the app told MyAnimeList, AniList,
 * Kitsu, Shikimori, Bangumi and Simkl what had been watched and never once listened — watch three
 * episodes somewhere else and the app still showed none of them seen. It is the most asked-for
 * thing about tracking upstream, and that guard is why it did not work, rather than a decision that
 * it should not.
 *
 * It runs for any tracker now when the user has asked for it, and for an enhanced one always. The
 * difference is not an inconsistency: an enhanced tracker *is* the source — Jellyfin is the server
 * the episodes were played from, so its progress is not a second opinion to reconcile but the
 * first-hand one. Every other tracker is a remote list that may or may not be ahead, and pulling
 * from it changes what the library says, which is a decision that belongs to whoever owns it.
 *
 * Whichever side is further ahead wins, and only *continuous* progress counts on the local side: an
 * episode counts as watched here when everything before it is. Someone who skipped ahead to episode
 * 40 has not watched 1 to 39, and taking the highest number would both mark all of them and push
 * that back out to the tracker.
 */
class SyncEpisodeProgressWithTrack(
    private val updateEpisode: UpdateEpisode,
    private val insertTrack: InsertAnimeTrack,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val trackPreferences: AnimeTrackPreferences,
) {

    suspend fun await(
        animeId: Long,
        remoteTrack: AnimeTrack,
        service: AnimeTracker,
    ) {
        if (!shouldSync(service)) return

        val sortedEpisodes = getEpisodesByAnimeId.await(animeId)
            .sortedBy { it.episodeNumber }
            .filter { it.isRecognizedNumber }

        val episodeUpdates = sortedEpisodes
            .filter { episode -> episode.episodeNumber <= remoteTrack.lastEpisodeSeen && !episode.seen }
            .map { it.copy(seen = true).toEpisodeUpdate() }

        val localLastSeen = sortedEpisodes.takeWhile { it.seen }.lastOrNull()?.episodeNumber ?: 0F
        val lastSeen = max(remoteTrack.lastEpisodeSeen, localLastSeen.toDouble())
        val updatedTrack = remoteTrack.copy(lastEpisodeSeen = lastSeen)

        try {
            service.update(updatedTrack.toDbTrack())
            updateEpisode.awaitAll(episodeUpdates)
            insertTrack.await(updatedTrack)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }

    /**
     * Kept separate, and internal, so the rule can be tested without a database behind it.
     */
    internal fun shouldSync(service: AnimeTracker): Boolean =
        service is EnhancedAnimeTracker || trackPreferences.syncProgressFromTracker().get()
}
