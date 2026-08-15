package animato.anime.ui.stats

/**
 * What the anime statistics screen shows.
 *
 * Mihon's `StatsScreenState` and `StatsData` are **sealed interfaces**, and Aniyomi added its anime
 * variants — `SuccessAnime`, `AnimeOverview`, `AnimeTitles`, `Episodes` — by editing those two
 * files. A sealed hierarchy can only be extended from the module that declares it, so that is not
 * available to us: the same wall phase 5c hit on `Preference`, in a second place.
 *
 * Which is fine here, and arguably better. Mihon's statistics screen and ours share no data at all —
 * one counts chapters read, the other counts episodes watched and seconds of video — so the only
 * thing a shared hierarchy bought was a shared file. These are our own types, in our own module,
 * and Mihon can rename or restructure its statistics without touching this.
 */
sealed interface AnimeStatsScreenState {

    data object Loading : AnimeStatsScreenState

    data class Success(
        val overview: AnimeStatsData.Overview,
        val titles: AnimeStatsData.Titles,
        val episodes: AnimeStatsData.Episodes,
        val trackers: AnimeStatsData.Trackers,
    ) : AnimeStatsScreenState
}

sealed interface AnimeStatsData {

    data class Overview(
        val libraryAnimeCount: Int,
        val completedAnimeCount: Int,
        val totalSeenDuration: Long,
    ) : AnimeStatsData

    data class Titles(
        val globalUpdateItemCount: Int,
        val startedAnimeCount: Int,
        val localAnimeCount: Int,
    ) : AnimeStatsData

    data class Episodes(
        val totalEpisodeCount: Int,
        val readEpisodeCount: Int,
        val downloadCount: Int,
    ) : AnimeStatsData

    data class Trackers(
        val trackedTitleCount: Int,
        val meanScore: Double,
        val trackerCount: Int,
    ) : AnimeStatsData
}
