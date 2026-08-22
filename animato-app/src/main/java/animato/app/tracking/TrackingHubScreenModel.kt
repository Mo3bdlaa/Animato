package animato.app.tracking

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.anime.track.AnimeTrackerManager
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.repository.TrackRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One tracking account.
 *
 * Deliberately an *account* and not a tracker-per-half. The anime trackers wrap Mihon's and share
 * their id, their stored credentials and their signed-in state — signing in on one signs in on both
 * — so listing AniList twice would invent a distinction that does not exist anywhere below the UI.
 * Two of them have no Mihon counterpart at all and appear once for the opposite reason.
 */
@Immutable
data class TrackingAccount(
    val id: Long,
    val name: String,
    val isLoggedIn: Boolean,
    val mangaTitles: Int,
    val animeTitles: Int,
    val lastSyncAt: Long,
    val isSyncing: Boolean = false,
    val failure: String? = null,
) {
    val titles: Int get() = mangaTitles + animeTitles
}

@Immutable
data class TrackingHubState(
    val isLoading: Boolean = true,
    val accounts: List<TrackingAccount> = emptyList(),
) {
    val signedIn: List<TrackingAccount> get() = accounts.filter { it.isLoggedIn }
}

/**
 * Tracking accounts and what they are doing, in one place.
 *
 * ## Why the screen exists
 *
 * Tracking was two settings screens: Mihon's list of manga trackers, and ours for the anime ones.
 * Both are lists of the same accounts with the same logins behind them, which meant the answer to
 * *am I signed in to AniList* depended on which screen you were looking at, and the answer to *is
 * anything actually syncing* was nowhere at all.
 *
 * ## Signing in is still theirs
 *
 * Each tracker's login is an OAuth flow or a credentials dialog written per tracker, and there are
 * a dozen of them. This screen does not reimplement any of that — *Sign in* opens the settings
 * screen that owns the dialog. What is new here is everything the settings screens never showed:
 * how many titles an account holds, when it last synced, and a button to sync it now.
 *
 * ## Sync in words
 *
 * *Synced 4m ago*, *12 titles*, *Failed · <reason>*. No progress ring: a ring is one title's state
 * and this screen is about accounts, so there is room for a sentence and a sentence is clearer.
 */
class TrackingHubScreenModel(
    private val trackerManager: TrackerManager = Injekt.get(),
    private val animeTrackerManager: AnimeTrackerManager = Injekt.get(),
    private val trackRepository: TrackRepository = Injekt.get(),
    private val animeTrackRepository: AnimeTrackRepository = Injekt.get(),
    private val refreshTracks: RefreshTracks = Injekt.get(),
    private val refreshAnimeTracks: RefreshAnimeTracks = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<TrackingHubState>
        field = MutableStateFlow(TrackingHubState())

    init {
        combine(
            trackerManager.loggedInTrackersFlow(),
            animeTrackerManager.loggedInTrackersFlow(),
            trackRepository.getTracksAsFlow(),
            animeTrackRepository.getAnimeTracksAsFlow(),
        ) { loggedInManga, loggedInAnime, mangaTracks, animeTracks ->
            val loggedInIds = (loggedInManga.map { it.id } + loggedInAnime.map { it.id }).toSet()
            val mangaCounts = mangaTracks.groupingBy { it.trackerId }.eachCount()
            val animeCounts = animeTracks.groupingBy { it.trackerId }.eachCount()

            accountIdsAndNames().map { (id, name) ->
                TrackingAccount(
                    id = id,
                    name = name,
                    isLoggedIn = id in loggedInIds,
                    mangaTitles = mangaCounts[id] ?: 0,
                    animeTitles = animeCounts[id] ?: 0,
                    lastSyncAt = lastSync(id).get(),
                )
            }
        }
            .onEach { accounts ->
                // Whatever is mid-sync stays mid-sync: the flow above re-emits every time a track
                // row is written, which is constantly during a sync it would otherwise erase.
                val syncing = state.value.accounts.filter { it.isSyncing }.map { it.id }.toSet()
                state.value = TrackingHubState(
                    isLoading = false,
                    accounts = accounts.map { it.copy(isSyncing = it.id in syncing) },
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Every account exactly once.
     *
     * Mihon's list first, then the anime-only ones — Simkl and Jellyfin — which are the two with no
     * counterpart to be deduplicated against.
     */
    private fun accountIdsAndNames(): List<Pair<Long, String>> {
        val manga = trackerManager.trackers.map { it.id to it.name }
        val mangaIds = manga.map { it.first }.toSet()
        val animeOnly = animeTrackerManager.trackers
            .filterNot { it.id in mangaIds }
            .map { it.id to it.name }
        return manga + animeOnly
    }

    /**
     * Pulls everything this account tracks, in both halves.
     *
     * The per-entry refresh is what both title pages already call; there was simply never anything
     * that called it for the whole library. Failures are counted rather than thrown — one dead
     * title must not stop the other two hundred — and the account's row says how many.
     */
    fun sync(account: TrackingAccount) {
        if (account.isSyncing) return
        setSyncing(account.id, true)

        viewModelScope.launchIO {
            /*
             * The row must stop spinning whatever happens, and "last synced" must mean it synced.
             *
             * Neither was true. The reads and refreshes had no catch, so a database failure or a
             * throw from `getTracks` — which sits outside the per-track supervisor inside
             * RefreshAnimeTracks — escaped to a scope with no handler, crashed the app, and left
             * the row spinning on the way out. And the timestamp was written unconditionally, so a
             * sync where every single title failed still reported itself as having just happened.
             */
            var failures = 0
            val synced = try {
                val mangaIds = trackRepository.getTracksAsFlow().first()
                    .filter { it.trackerId == account.id }
                    .map { it.mangaId }
                    .distinct()
                val animeIds = animeTrackRepository.getAnimeTracksAsFlow().first()
                    .filter { it.trackerId == account.id }
                    .map { it.animeId }
                    .distinct()

                mangaIds.forEach { failures += refreshTracks.await(it).size }
                animeIds.forEach { failures += refreshAnimeTracks.await(it).size }
                // Nothing attempted is not a failed sync; everything attempted having failed is.
                failures == 0 || failures < mangaIds.size + animeIds.size
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Tracker sync failed for ${account.id}" }
                failures++
                false
            }

            if (synced) lastSync(account.id).set(System.currentTimeMillis())
            setSyncing(account.id, false, failures = failures)
        }
    }

    fun syncAll() = state.value.signedIn.forEach(::sync)

    private fun setSyncing(id: Long, syncing: Boolean, failures: Int = 0) {
        state.value = state.value.copy(
            accounts = state.value.accounts.map { account ->
                if (account.id != id) {
                    account
                } else {
                    account.copy(
                        isSyncing = syncing,
                        lastSyncAt = if (syncing) account.lastSyncAt else lastSync(id).get(),
                        failure = failures.takeIf { it > 0 }?.toString(),
                    )
                }
            },
        )
    }

    /**
     * When this account last finished a sync.
     *
     * Ours, and app state rather than a setting — nobody chooses it and nobody should find it in a
     * backup. Neither half records it, because neither half had a screen that could show it.
     */
    private fun lastSync(id: Long) = preferenceStore.getLong("animato_tracker_last_sync_$id", 0L)
}
