package animato.anime.track.shikimori.dto

import animato.anime.track.shikimori.AnimeShikimori
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAiredDate
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMPersonRole
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMPoster
import kotlinx.serialization.Serializable

/**
 * What Shikimori answers with about anime.
 *
 * The donor still asks Shikimori's REST endpoints for this; Mihon has since moved its search and
 * list reads to GraphQL, and this follows Mihon. Writing an entry is still `/v2/user_rates` in
 * both, and that has not moved.
 *
 * The small pieces — a poster, an aired date, a person's role — are Mihon's models, because they
 * are the same objects whichever query returned them.
 */
@Serializable
data class SMAnimeSearchResult(
    val data: SMAnimeResults,
)

@Serializable
data class SMAnimeResults(
    val animes: List<SMAnime> = emptyList(),
)

@Serializable
data class SMAnime(
    val id: Long,
    val name: String = "",
    val episodes: Long = 0,
    val score: Double? = null,
    val url: String = "",
    val status: String? = null,
    val poster: SMPoster? = null,
    val airedOn: SMAiredDate? = null,
    val description: String? = null,
    val kind: String? = null,
    val personRoles: List<SMPersonRole>? = null,
    val userRate: SMAnimeUserRate? = null,
) {

    fun toTrackSearch(trackId: Long) = AnimeTrackSearch.create(trackId).also {
        it.remote_id = id
        it.title = name
        it.total_episodes = episodes
        it.cover_url = poster?.mainUrl.orEmpty()
        it.summary = description.orEmpty()
        // Shikimori sends 0 for "not rated", which would otherwise show as a score of zero.
        it.score = score?.takeIf { value -> value > 0.0 } ?: -1.0
        it.tracking_url = url
        it.publishing_status = status.orEmpty()
        it.publishing_type = kind.orEmpty()
        it.start_date = airedOn?.date.orEmpty()
        personRoles?.forEach { personRole ->
            personRole.roles.forEach { role ->
                // Shikimori files a studio under "Director" for anime; both are worth showing.
                if ("Director" in role || "Story" in role) it.authors += personRole.person.name
            }
        }

        // Present only when the anime is on the user's list, which is how absence is told apart
        // from a fresh entry.
        userRate?.let { rate ->
            it.library_id = rate.rateId.toLongOrNull()
            it.last_episode_seen = rate.episodes.toDouble()
            it.score = rate.score
            it.status = AnimeShikimori.statusFrom(rate.status)
        }
    }

    val isOnUserList: Boolean get() = userRate != null
}

@Serializable
data class SMAnimeUserRate(
    // The id of the list entry, not of the anime.
    val id: String = "",
    val episodes: Long = 0,
    val status: String = "",
    val score: Double = 0.0,
) {
    val rateId: String get() = id
}
