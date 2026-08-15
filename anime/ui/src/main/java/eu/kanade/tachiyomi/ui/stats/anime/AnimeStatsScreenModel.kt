package eu.kanade.tachiyomi.ui.stats.anime

import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.track.AnimeTrackerManager
import animato.anime.ui.stats.AnimeStatsData
import animato.anime.ui.stats.AnimeStatsScreenState
import aniyomi.domain.library.service.AnimeLibraryPreferences
import aniyomi.domain.library.service.AnimeLibraryPreferences.Companion.ANIME_HAS_UNSEEN
import aniyomi.domain.library.service.AnimeLibraryPreferences.Companion.ANIME_NON_COMPLETED
import aniyomi.domain.library.service.AnimeLibraryPreferences.Companion.ANIME_NON_SEEN
import eu.kanade.core.util.fastCountNot
import eu.kanade.core.util.fastFilterNot
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.animeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeStatsScreenModel(
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val getAnimelibAnime: GetLibraryAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val preferences: LibraryPreferences = Injekt.get(),
    private val animePreferences: AnimeLibraryPreferences = Injekt.get(),
    private val trackerManager: AnimeTrackerManager = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<AnimeStatsScreenState>
        field = MutableStateFlow<AnimeStatsScreenState>(AnimeStatsScreenState.Loading)

    private val loggedInTrackers by lazy { trackerManager.loggedInTrackers() }

    init {
        viewModelScope.launchIO {
            val animelibAnime = getAnimelibAnime.await()

            val distinctLibraryAnime = animelibAnime.fastDistinctBy { it.id }

            val animeTrackMap = getAnimeTrackMap(distinctLibraryAnime)
            val scoredAnimeTrackerMap = getScoredAnimeTrackMap(animeTrackMap)

            val meanScore = getTrackMeanScore(scoredAnimeTrackerMap)

            val overviewStatData = AnimeStatsData.Overview(
                libraryAnimeCount = distinctLibraryAnime.size,
                completedAnimeCount = distinctLibraryAnime.count {
                    it.anime.status.toInt() == SAnime.COMPLETED && it.unseenCount == 0L
                },
                totalSeenDuration = getWatchTime(distinctLibraryAnime),
            )

            val titlesStatData = AnimeStatsData.Titles(
                globalUpdateItemCount = getGlobalUpdateItemCount(animelibAnime),
                startedAnimeCount = distinctLibraryAnime.count { it.hasStarted },
                localAnimeCount = distinctLibraryAnime.count { it.anime.isLocal() },
            )

            val chaptersStatData = AnimeStatsData.Episodes(
                totalEpisodeCount = distinctLibraryAnime.sumOf { it.totalCount }.toInt(),
                readEpisodeCount = distinctLibraryAnime.sumOf { it.seenCount }.toInt(),
                downloadCount = downloadManager.getDownloadCount(),
            )

            val trackersStatData = AnimeStatsData.Trackers(
                trackedTitleCount = animeTrackMap.count { it.value.isNotEmpty() },
                meanScore = meanScore,
                trackerCount = loggedInTrackers.size,
            )

            state.update {
                AnimeStatsScreenState.Success(
                    overview = overviewStatData,
                    titles = titlesStatData,
                    episodes = chaptersStatData,
                    trackers = trackersStatData,
                )
            }
        }
    }

    private fun getGlobalUpdateItemCount(libraryAnime: List<LibraryAnime>): Int {
        val includedCategories = animePreferences.animeUpdateCategories().get().map { it.toLong() }
        val includedAnime = if (includedCategories.isNotEmpty()) {
            libraryAnime.filter { it.category in includedCategories }
        } else {
            libraryAnime
        }

        val excludedCategories = animePreferences.animeUpdateCategoriesExclude().get().map { it.toLong() }
        val excludedMangaIds = if (excludedCategories.isNotEmpty()) {
            libraryAnime.fastMapNotNull { anime ->
                anime.id.takeIf { anime.category in excludedCategories }
            }
        } else {
            emptyList()
        }

        val updateRestrictions = preferences.autoUpdateMangaRestrictions.get()
        return includedAnime
            .fastFilterNot { it.anime.id in excludedMangaIds }
            .fastDistinctBy { it.anime.id }
            .fastCountNot {
                (ANIME_NON_COMPLETED in updateRestrictions && it.anime.status.toInt() == SAnime.COMPLETED) ||
                    (ANIME_HAS_UNSEEN in updateRestrictions && it.unseenCount != 0L) ||
                    (ANIME_NON_SEEN in updateRestrictions && it.totalCount > 0 && !it.hasStarted)
            }
    }

    private suspend fun getAnimeTrackMap(libraryAnime: List<LibraryAnime>): Map<Long, List<AnimeTrack>> {
        val loggedInTrackerIds = loggedInTrackers.map { it.id }.toHashSet()
        return libraryAnime.associate { anime ->
            val tracks = getTracks.await(anime.id)
                .fastFilter { it.trackerId in loggedInTrackerIds }

            anime.id to tracks
        }
    }

    private suspend fun getWatchTime(libraryAnimeList: List<LibraryAnime>): Long {
        var watchTime = 0L
        libraryAnimeList.forEach { libraryAnime ->
            getEpisodesByAnimeId.await(libraryAnime.anime.id).forEach { episode ->
                watchTime += if (episode.seen) {
                    episode.totalSeconds
                } else {
                    episode.lastSecondSeen
                }
            }
        }

        return watchTime
    }

    private fun getScoredAnimeTrackMap(animeTrackMap: Map<Long, List<AnimeTrack>>): Map<Long, List<AnimeTrack>> {
        return animeTrackMap.mapNotNull { (animeId, tracks) ->
            val trackList = tracks.mapNotNull { track ->
                track.takeIf { it.score > 0.0 }
            }
            if (trackList.isEmpty()) return@mapNotNull null
            animeId to trackList
        }.toMap()
    }

    private fun getTrackMeanScore(scoredAnimeTrackMap: Map<Long, List<AnimeTrack>>): Double {
        return scoredAnimeTrackMap
            .map { (_, tracks) ->
                tracks.map(::get10PointScore).average()
            }
            .fastFilter { !it.isNaN() }
            .average()
    }

    private fun get10PointScore(track: AnimeTrack): Double {
        val service = trackerManager.get(track.trackerId)!!
        return service.get10PointScore(track)
    }
}
