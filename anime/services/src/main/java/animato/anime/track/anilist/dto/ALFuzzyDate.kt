package animato.anime.track.anilist.dto

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable

/**
 * AniList's partial date. Carried from Mihon, which deleted it when its own AniList tracker moved
 * to generated GraphQL types; the anime tracker still speaks the plain JSON API and needs it.
 */
@Serializable
data class ALFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?,
) {
    fun toEpochMilli(): Long = try {
        LocalDate(year!!, month!!, day!!)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()
    } catch (_: Exception) {
        0L
    }
}
