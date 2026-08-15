package animato.anime.track

import animato.anime.track.anilist.AnimeAnilist
import animato.anime.track.bangumi.AnimeBangumi
import animato.anime.track.kitsu.AnimeKitsu
import animato.anime.track.myanimelist.AnimeMyAnimeList
import animato.anime.track.shikimori.AnimeShikimori
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * The trackers that can track an anime.
 *
 * Mihon's `TrackerManager` is a final class holding a fixed list, so there is no adding to it. This
 * stands beside it rather than replacing it: each entry here wraps the Mihon tracker of the same
 * name and shares its identity completely — the same id, the same stored credentials, the same
 * signed-in state. Signing in on Mihon's tracking screen signs in here too, and signing out signs
 * out of both, because there is only one account and one token underneath.
 *
 * Not every tracker appears. A tracker is here when it has an anime API and somebody has written
 * the queries for it; the rest of Mihon's list is manga-only and is not missing anything.
 */
class AnimeTrackerManager(
    trackerManager: TrackerManager = Injekt.get(),
) {

    val myAnimeList = AnimeMyAnimeList(trackerManager.myAnimeList)
    val aniList = AnimeAnilist(trackerManager.aniList)
    val kitsu = AnimeKitsu(trackerManager.kitsu)
    val shikimori = AnimeShikimori(trackerManager.shikimori)
    val bangumi = AnimeBangumi(trackerManager.bangumi)

    val trackers: List<AnimeTracker> = listOf(myAnimeList, aniList, kitsu, shikimori, bangumi)

    /**
     * The ones the user is signed in to.
     *
     * Signed-in is asked of the underlying Mihon tracker, which is the only thing that knows.
     */
    fun loggedInTrackers(): List<AnimeTracker> = trackers.filter { it.isLoggedIn }

    fun loggedInTrackersFlow(): Flow<List<AnimeTracker>> {
        val flows = trackers.map { it.isLoggedInFlow }
        if (flows.isEmpty()) return flowOf(emptyList())
        return combine(flows) { states ->
            trackers.filterIndexed { index, _ -> states[index] }
        }
    }

    fun get(id: Long): AnimeTracker? = trackers.find { it.id == id }

    fun getAll(ids: Set<Long>): List<AnimeTracker> = trackers.filter { it.id in ids }
}
