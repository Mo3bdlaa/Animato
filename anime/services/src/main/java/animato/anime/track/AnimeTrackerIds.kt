package animato.anime.track

/**
 * The ids of the trackers that exist only on the anime side.
 *
 * The five wrapping trackers take their ids from the Mihon tracker they wrap, which is what makes
 * them share its credentials. These two have nobody to take an id from, so they carry Aniyomi's —
 * unchanged, and that is the point: a track row records the id of the tracker that made it, so an
 * imported Aniyomi backup only keeps its Simkl and Jellyfin links if the numbers still mean the
 * same thing.
 *
 * They sit far above Mihon's range, which reaches 11, so there is room for upstream to keep
 * numbering without ever reaching these.
 */
object AnimeTrackerIds {
    /*
     * Two of the wrapped trackers' ids, written out because Mihon stopped exposing them as
     * constants. They are Mihon's numbers, not ours — `TrackerManager` builds AniList and Kitsu
     * with exactly these — and must stay in step with it for the shared credentials to line up.
     */
    const val ANILIST = 2L
    const val KITSU = 3L

    const val SIMKL = 101L
    const val JELLYFIN = 102L
}
