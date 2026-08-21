package animato.anime.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Jellyfin's API, as much of it as this app asks about.
 *
 * Every field is optional with a default. A Jellyfin server answers with whatever its version and
 * its plugins decided to include, an Emby server answers with a near-but-not-identical shape, and
 * the two are the same code path here. A required field would mean one absent key turning a whole
 * library into a parse error — which on screen is a server that "stopped working" after an update
 * nobody here made.
 */

/** What comes back from `POST /Users/AuthenticateByName`. */
@Serializable
data class JellyfinAuthResponse(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("ServerId") val serverId: String = "",
    @SerialName("User") val user: JellyfinUser = JellyfinUser(),
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

/** A page of items, or the user's libraries — Jellyfin returns both in this envelope. */
@Serializable
data class JellyfinItems(
    @SerialName("Items") val items: List<JellyfinItem> = emptyList(),
    /**
     * How many there are in total, ignoring the page.
     *
     * The only way to know whether to ask for another page: Jellyfin has no "there is more" flag,
     * and a full page is not proof of a next one.
     */
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

@Serializable
data class JellyfinItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    /** `Movie`, `Series`, `Season`, `Episode` — and for a library view, `CollectionFolder`. */
    @SerialName("Type") val type: String = "",
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Studios") val studios: List<JellyfinNamed> = emptyList(),
    @SerialName("People") val people: List<JellyfinPerson> = emptyList(),
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    /**
     * Which library a view is: `movies`, `tvshows`, `music`, `books`…
     *
     * Only set on the entries returned by `/Views`, which is exactly where it is read — it is how
     * the music and book libraries are kept out of a video app's catalogue.
     */
    @SerialName("CollectionType") val collectionType: String? = null,
    /**
     * Image tags, keyed by kind — `Primary`, `Backdrop`, `Thumb`.
     *
     * The tag matters rather than merely existing: it is the cache key in the image URL, so a
     * cover replaced on the server changes its tag and the app fetches the new one instead of
     * showing the old one until something clears the cache.
     */
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: JellyfinUserData? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
)

@Serializable
data class JellyfinNamed(
    @SerialName("Name") val name: String = "",
)

@Serializable
data class JellyfinPerson(
    @SerialName("Name") val name: String = "",
    /** `Actor`, `Director`, `Writer`, `Producer`, `GuestStar`. */
    @SerialName("Type") val type: String = "",
)

@Serializable
data class JellyfinUserData(
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
)

/**
 * A progress report, as the server expects it.
 *
 * `IsPaused` is always false: this is only sent while something is actually playing, and a paused
 * report would show the app as an idle session in the server's dashboard for as long as the pause
 * lasted.
 */
@Serializable
data class JellyfinProgress(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false,
)
