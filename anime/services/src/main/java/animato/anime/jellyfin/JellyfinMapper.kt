package animato.anime.jellyfin

import animato.anime.content.EntryForm
import animato.anime.content.SourceProgress
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.EMPTY
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Jellyfin's vocabulary translated into ours.
 *
 * Kept apart from [JellyfinSource] for the same reason the Stremio one is: everything here is a
 * pure function of a parsed response, so the parts most likely to be wrong — how an episode is
 * numbered across seasons, what a film's single row is called, which of the twenty people attached
 * to an item is the director — can be checked without a server.
 */
internal object JellyfinMapper {

    /**
     * How an item is addressed once it leaves here.
     *
     * The type and the id, joined by the first colon, exactly as the Stremio side does — and for
     * the same reason, since [SAnime.url] is the only field that survives into the library and both
     * halves of the address have to fit in it. Jellyfin ids are hex GUIDs and contain no colon, so
     * the split is unambiguous either way; it is written this way to match rather than because it
     * has to be.
     */
    fun entryUrl(type: String, id: String): String = "$type:$id"

    fun parseEntryUrl(url: String): Pair<String, String>? {
        val separator = url.indexOf(':')
        if (separator <= 0 || separator == url.lastIndex) return null
        return url.substring(0, separator) to url.substring(separator + 1)
    }

    /** A film is complete; a series arrives an episode at a time. Nothing here is ever live. */
    fun formOf(entryUrl: String): EntryForm =
        if (parseEntryUrl(entryUrl)?.first == TYPE_MOVIE) EntryForm.Single else EntryForm.Serial

    fun toSAnime(item: JellyfinItem, base: String): SAnime = SAnime.create().apply {
        url = entryUrl(item.type, item.id)
        title = item.name
        thumbnail_url = JellyfinUrls.image(base, item.id, item.imageTags[JellyfinUrls.IMAGE_PRIMARY])
        background_url = item.backdropImageTags.firstOrNull()?.let {
            JellyfinUrls.image(base, item.id, it, JellyfinUrls.IMAGE_BACKDROP, BACKDROP_HEIGHT)
        }
        description = buildDescription(item)
        genre = item.genres.joinToString(", ").takeIf { it.isNotEmpty() }
        author = item.people.firstOrNull { it.type == PERSON_DIRECTOR }?.name
            // A film usually credits a director and a series usually does not; the studio is what
            // the row would otherwise leave blank for every series on the server.
            ?: item.studios.firstOrNull()?.name
        artist = item.people.filter { it.type in ACTOR_TYPES }
            .take(CAST_SHOWN)
            .joinToString(", ") { it.name }
            .takeIf { it.isNotEmpty() }
        status = statusOf(item)
        fetch_type = FetchType.Episodes
        // A film's one row never changes and a series' does. Asking the server about every film in
        // a library on every update cycle is a request per film that can only ever say the same
        // thing, and a personal server is exactly where that is most noticeable.
        update_strategy = if (item.type == TYPE_MOVIE) {
            AnimeUpdateStrategy.ONLY_FETCH_ONCE
        } else {
            AnimeUpdateStrategy.ALWAYS_UPDATE
        }
    }

    /**
     * The episodes of a series, newest first.
     *
     * Numbered by position in the sorted list rather than by `IndexNumber`. Across seasons the
     * episode number restarts at one, and two rows sharing a number are two rows the app cannot
     * tell apart — the same reasoning as the Stremio side, arrived at the same way.
     *
     * Reversed at the end for the same reason too: `sourceOrder` is read with "0 is the newest",
     * which is what makes the default sort put the latest episode on top.
     */
    fun toEpisodes(episodes: List<JellyfinItem>): List<SEpisode> = episodes
        .sortedWith(
            compareBy(
                // Specials are season 0 on Jellyfin as everywhere else, and belong after the run
                // rather than before it — numbering them first would renumber the whole series.
                { if (it.parentIndexNumber == SPECIALS_SEASON) 1 else 0 },
                { it.parentIndexNumber ?: Int.MAX_VALUE },
                { it.indexNumber ?: Int.MAX_VALUE },
                { it.premiereDate.orEmpty() },
            ),
        )
        .mapIndexed { index, episode ->
            SEpisode.create().apply {
                url = entryUrl(TYPE_EPISODE, episode.id)
                name = episodeName(episode)
                episode_number = (index + 1).toFloat()
                date_upload = parseDate(episode.premiereDate)
                summary = episode.overview
                memo = progressMemo(episode)
            }
        }
        .reversed()

    /**
     * The one row a film has.
     *
     * Named after the film rather than "Episode 1", which is the same choice the Stremio side
     * makes and is what `EntryForm.Single` exists to let the rest of the app understand.
     */
    fun toSingleEpisode(item: JellyfinItem): SEpisode = SEpisode.create().apply {
        url = entryUrl(TYPE_MOVIE, item.id)
        name = item.name.takeIf { it.isNotBlank() } ?: DEFAULT_SINGLE_NAME
        episode_number = 1f
        date_upload = parseDate(item.premiereDate)
        summary = item.overview
        memo = progressMemo(item)
    }

    /**
     * Which libraries are worth offering as categories.
     *
     * Music, books and photos are libraries on the same server and are not this app's business.
     * A library with no declared type is kept: Jellyfin leaves `CollectionType` unset for a mixed
     * library, and dropping those would hide the shelf of somebody who never told their server what
     * kind of thing they were storing.
     */
    fun videoLibraries(views: List<JellyfinItem>): List<JellyfinItem> = views.filterNot {
        it.collectionType?.lowercase() in NON_VIDEO_LIBRARIES
    }

    /**
     * What the server already knows about this episode, carried across.
     *
     * The reason someone signs a server in and finds their history intact rather than starting from
     * nothing on a library they have been watching for years. Ticks are Jellyfin's unit — ten
     * thousand to the millisecond — and are converted here so nothing downstream has to know that.
     *
     * Only ever read for an episode this app has not stored before; see [SourceProgress].
     */
    private fun progressMemo(item: JellyfinItem): JsonObject {
        val data = item.userData ?: return JsonObject.EMPTY
        return SourceProgress.memoOf(
            seen = data.played,
            positionMs = data.playbackPositionTicks / TICKS_PER_MILLISECOND,
        )
    }

    private fun episodeName(episode: JellyfinItem): String {
        val season = episode.parentIndexNumber
        val number = episode.indexNumber
        val label = when {
            season != null && number != null -> "S%d:E%d".format(season, number)
            number != null -> "E$number"
            else -> null
        }
        val title = episode.name.takeIf { it.isNotBlank() }
        return listOfNotNull(label, title).joinToString(" · ").ifEmpty { UNTITLED_EPISODE }
    }

    private fun buildDescription(item: JellyfinItem): String? {
        val year = item.productionYear?.toString()
        val overview = item.overview?.takeIf { it.isNotBlank() }
        return listOfNotNull(year, overview).joinToString("\n\n").takeIf { it.isNotEmpty() }
    }

    /**
     * Whether the series has finished.
     *
     * Jellyfin says `Continuing` or `Ended` for a series and says nothing for a film. A film is
     * complete by definition, which is a more useful answer than *unknown* and is the one thing
     * about a film's status that is always true.
     */
    private fun statusOf(item: JellyfinItem): Int = when {
        item.type == TYPE_MOVIE -> SAnime.COMPLETED
        item.status.equals(STATUS_ENDED, ignoreCase = true) -> SAnime.COMPLETED
        item.status.equals(STATUS_CONTINUING, ignoreCase = true) -> SAnime.ONGOING
        else -> SAnime.UNKNOWN
    }

    /**
     * A date, or zero.
     *
     * Zero rather than a throw and rather than *now*: the row draws the date only when it is set,
     * so an unreadable one costs the row its date and nothing else. Jellyfin sends ISO-8601 with a
     * zone; a server that has been through a migration occasionally sends something else, and that
     * is not worth an empty episode list.
     */
    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0L
        }
    }

    const val TYPE_MOVIE = "Movie"
    const val TYPE_SERIES = "Series"
    const val TYPE_EPISODE = "Episode"

    /** Jellyfin counts in ticks of a hundred nanoseconds. */
    const val TICKS_PER_MILLISECOND = 10_000L

    private const val SPECIALS_SEASON = 0
    private const val CAST_SHOWN = 4
    private const val BACKDROP_HEIGHT = 1080
    private const val PERSON_DIRECTOR = "Director"
    private const val DEFAULT_SINGLE_NAME = "Film"
    private const val UNTITLED_EPISODE = "Episode"
    private const val STATUS_ENDED = "Ended"
    private const val STATUS_CONTINUING = "Continuing"

    private val ACTOR_TYPES = setOf("Actor", "GuestStar")
    private val NON_VIDEO_LIBRARIES = setOf("music", "books", "photos", "musicvideos", "playlists")
}
