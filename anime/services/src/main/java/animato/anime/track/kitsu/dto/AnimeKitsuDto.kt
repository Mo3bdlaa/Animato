package animato.anime.track.kitsu.dto

import animato.anime.track.kitsu.AnimeKitsu
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.kitsu.KitsuDateHelper
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuSearchItemCover
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What Kitsu answers with about anime.
 *
 * Mihon's models ask for `chapterCount` and `mangaType`; the anime endpoints answer with
 * `episodeCount` and `showType`. `KitsuSearchItemCover` and `KitsuDateHelper` are Mihon's,
 * unchanged — a cover and a date do not differ by media type.
 */
@Serializable
data class KitsuAnimeSearchResult(
    val hits: List<KitsuAnimeSearchItem> = emptyList(),
)

@Serializable
data class KitsuAnimeSearchItem(
    val id: Long,
    val canonicalTitle: String = "",
    val episodeCount: Long? = null,
    val subtype: String? = null,
    val posterImage: KitsuSearchItemCover? = null,
    val synopsis: String? = null,
    val averageRating: Double? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
) {

    fun toTrackSearch() = AnimeTrackSearch.create(TrackerManager.KITSU).also {
        it.remote_id = id
        it.title = canonicalTitle
        it.total_episodes = episodeCount ?: 0
        it.cover_url = posterImage?.original.orEmpty()
        it.summary = synopsis.orEmpty()
        it.tracking_url = AnimeKitsu.animeUrl(id)
        it.score = averageRating ?: -1.0
        // Kitsu says when something finished rather than whether it is still going.
        it.publishing_status = if (endDate == null) "Airing" else "Finished"
        it.publishing_type = subtype.orEmpty()
        // Algolia gives seconds; the rest of the app works in milliseconds.
        it.start_date = startDate?.let { seconds ->
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(seconds * 1000))
        }.orEmpty()
    }
}

/**
 * A library entry, which Kitsu answers as the user's progress in `data` and the anime itself in
 * `included` — two lists that have to be read together.
 */
@Serializable
data class KitsuAnimeListResult(
    val data: List<KitsuAnimeListEntry> = emptyList(),
    val included: List<KitsuAnimeListIncluded> = emptyList(),
) {

    val isEmpty: Boolean get() = data.isEmpty() || included.isEmpty()

    fun firstToTrack(): AnimeTrackSearch {
        require(data.isNotEmpty()) { "Missing user data from Kitsu" }
        require(included.isNotEmpty()) { "Missing anime data from Kitsu" }

        val entry = data[0]
        val progress = entry.attributes
        val anime = included[0].attributes

        return AnimeTrackSearch.create(TrackerManager.KITSU).also {
            it.remote_id = included[0].id
            it.library_id = entry.id
            it.title = anime.canonicalTitle
            it.total_episodes = anime.episodeCount ?: 0
            it.cover_url = anime.posterImage?.original.orEmpty()
            it.summary = anime.synopsis.orEmpty()
            it.tracking_url = AnimeKitsu.animeUrl(included[0].id)
            it.started_watching_date = KitsuDateHelper.parse(progress.startedAt)
            it.finished_watching_date = KitsuDateHelper.parse(progress.finishedAt)
            it.status = when (progress.status) {
                "current" -> AnimeKitsu.WATCHING
                "completed" -> AnimeKitsu.COMPLETED
                "on_hold" -> AnimeKitsu.ON_HOLD
                "dropped" -> AnimeKitsu.DROPPED
                "planned" -> AnimeKitsu.PLAN_TO_WATCH
                else -> throw Exception("Unknown Kitsu status: ${progress.status}")
            }
            // Kitsu scores out of twenty; the app's scale is ten.
            it.score = progress.ratingTwenty?.let { rating -> rating / 2.0 } ?: 0.0
            it.last_episode_seen = progress.progress.toDouble()
            it.private = progress.private
        }
    }
}

@Serializable
data class KitsuAnimeListEntry(
    val id: Long,
    val attributes: KitsuAnimeListEntryAttributes,
)

@Serializable
data class KitsuAnimeListEntryAttributes(
    val status: String = "",
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val ratingTwenty: Int? = null,
    val progress: Int = 0,
    val private: Boolean = false,
)

@Serializable
data class KitsuAnimeListIncluded(
    val id: Long,
    val attributes: KitsuAnimeListIncludedAttributes,
)

@Serializable
data class KitsuAnimeListIncludedAttributes(
    val canonicalTitle: String = "",
    val episodeCount: Long? = null,
    val showType: String? = null,
    val posterImage: KitsuSearchItemCover? = null,
    val synopsis: String? = null,
    val startDate: String? = null,
    val status: String = "",
)

@Serializable
data class KitsuAddAnimeResult(
    val data: KitsuAddAnimeItem,
)

@Serializable
data class KitsuAddAnimeItem(
    val id: Long,
)
