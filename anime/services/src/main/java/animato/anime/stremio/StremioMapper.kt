package animato.anime.stremio

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Stremio's vocabulary translated into ours.
 *
 * Kept apart from [StremioSource] on purpose: everything here is a pure function of a parsed
 * response, so the parts most likely to be wrong — episode ordering across seasons, which streams
 * are actually playable, how a torrent becomes a URL our player understands — can be checked
 * without a network or a device.
 */
internal object StremioMapper {

    /**
     * How an entry is addressed once it leaves here.
     *
     * [SAnime.url] is the only field that survives into the library, so it has to carry both
     * halves of a Stremio address: the content type and the id. They are joined with the first
     * colon and split on the first colon, because the type never contains one and the id very
     * often does — `series:tt0944947:1:1` is a perfectly ordinary episode.
     */
    fun entryUrl(type: String, id: String): String = "$type:$id"

    fun parseEntryUrl(url: String): Pair<String, String>? {
        val separator = url.indexOf(':')
        if (separator <= 0 || separator == url.lastIndex) return null
        return url.substring(0, separator) to url.substring(separator + 1)
    }

    fun toSAnime(preview: StremioMetaPreview, fallbackType: String): SAnime = SAnime.create().apply {
        val type = preview.type?.takeIf { it.isNotBlank() } ?: fallbackType
        url = entryUrl(type, preview.id)
        title = preview.name
        thumbnail_url = preview.poster
        description = preview.description
        genre = (preview.genres + preview.genre).distinct().joinToString(", ").takeIf { it.isNotEmpty() }
        fetch_type = FetchType.Episodes
        update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
    }

    fun toSAnime(meta: StremioMeta, fallbackType: String): SAnime = SAnime.create().apply {
        val type = meta.type?.takeIf { it.isNotBlank() } ?: fallbackType
        url = entryUrl(type, meta.id)
        title = meta.name
        thumbnail_url = meta.poster
        background_url = meta.background
        description = buildDescription(meta)
        genre = meta.allGenres.joinToString(", ").takeIf { it.isNotEmpty() }
        author = meta.director.firstOrNull()
        artist = meta.cast.take(CAST_IN_ARTIST).joinToString(", ").takeIf { it.isNotEmpty() }
        status = statusOf(meta)
        // Declared here and nowhere earlier, because this is the first moment anything knows. A
        // catalogue entry carries no videos, so the season count is unknowable until the meta
        // arrives — and the app fixes an entry's fetch type once it is initialised, which is what
        // this call does.
        fetch_type = if (toSeasons(meta, fallbackType).isEmpty()) FetchType.Episodes else FetchType.Seasons
        update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
        initialized = true
    }

    /**
     * The videos of an entry, in the order a person would watch them.
     *
     * Two shapes arrive here. A series carries a `videos` array with season and episode numbers,
     * out of order as often as not, with specials filed under season 0. A film carries no videos
     * at all — its stream is fetched under the entry's own id — so one is synthesised, because a
     * title page with an empty episode list reads as broken rather than as "this is a film".
     *
     * Numbering is a running index over the sorted list rather than the episode field itself:
     * across several seasons the episode field restarts at 1, and two episodes sharing a number
     * are two episodes the app cannot tell apart. Specials sort last for the same reason they are
     * season 0 — they are extra, and putting them first pushes episode one off the screen.
     */
    fun toEpisodes(meta: StremioMeta, fallbackType: String, onlySeason: Int? = null): List<SEpisode> {
        val type = meta.type?.takeIf { it.isNotBlank() } ?: fallbackType
        if (meta.videos.isEmpty()) {
            return listOf(
                SEpisode.create().apply {
                    url = entryUrl(type, meta.id)
                    name = meta.name.takeIf { it.isNotBlank() } ?: DEFAULT_SINGLE_EPISODE_NAME
                    episode_number = 1f
                    date_upload = parseReleaseDate(meta.releaseInfo.primitiveText())
                },
            )
        }

        return meta.videos
            .filter { onlySeason == null || it.season == onlySeason }
            .sortedWith(
                compareBy(
                    { if (it.season == SPECIALS_SEASON) 1 else 0 },
                    { it.season ?: Int.MAX_VALUE },
                    { it.episode ?: Int.MAX_VALUE },
                    { it.released.orEmpty() },
                ),
            )
            .mapIndexed { index, video ->
                SEpisode.create().apply {
                    url = entryUrl(type, video.id)
                    name = episodeName(video)
                    episode_number = (index + 1).toFloat()
                    date_upload = parseReleaseDate(video.released)
                    summary = video.overview ?: video.description
                    preview_url = video.thumbnail
                }
            }
    }

    /**
     * The distinct seasons of a series, each as an entry of its own.
     *
     * Stremio hands over every episode of every season in one document with the season stamped on
     * each, which is the whole reason this is cheap here and expensive everywhere else: no second
     * request, no guessing from titles, no numbering scheme to reverse-engineer. Specials keep
     * their own season 0 rather than being folded in — the addon said they were separate and it is
     * the one that knows.
     *
     * Returns nothing for a single-season series, which is how the caller decides not to insert a
     * layer: one season behind a tap called "Season 1" is a worse title page than the episode list
     * it replaced.
     */
    fun toSeasons(meta: StremioMeta, fallbackType: String): List<SAnime> {
        val type = meta.type?.takeIf { it.isNotBlank() } ?: fallbackType
        val numbers = meta.videos.mapNotNull { it.season }.distinct().sorted()
        if (numbers.size < 2) return emptyList()

        return numbers
            .sortedBy { if (it == SPECIALS_SEASON) Int.MAX_VALUE else it }
            .map { number ->
                SAnime.create().apply {
                    url = seasonUrl(type, meta.id, number)
                    title = seasonTitle(meta.name, number)
                    thumbnail_url = meta.poster
                    season_number = number.toDouble()
                    fetch_type = FetchType.Episodes
                    update_strategy = AnimeUpdateStrategy.ALWAYS_UPDATE
                }
            }
    }

    /**
     * A season's address: the entry's own, with the season appended after a slash.
     *
     * A slash rather than another colon, because colons are already the separator between type and
     * id *and* part of the ids themselves. One more of them would make the string ambiguous in a
     * way no amount of care at the parse site could undo.
     */
    fun seasonUrl(type: String, id: String, season: Int): String = "${entryUrl(type, id)}/s$season"

    /** The id and season back out of an address written by [seasonUrl], or null if it has none. */
    fun parseSeasonUrl(url: String): Pair<String, Int>? {
        val marker = url.lastIndexOf("/s")
        if (marker < 0) return null
        val season = url.substring(marker + 2).toIntOrNull() ?: return null
        return url.substring(0, marker) to season
    }

    private fun seasonTitle(name: String, number: Int): String = when (number) {
        SPECIALS_SEASON -> "$name — Specials"
        else -> "$name — Season $number"
    }

    /**
     * The streams we can actually play, as videos.
     *
     * A stream sets exactly one of four fields, and only two of them mean anything to us: a
     * direct URL, or a torrent we hand to the bundled torrent server. `ytId` and `externalUrl`
     * belong to other apps — listing them would fill the quality picker with entries that fail
     * only once tapped, which is worse than a shorter list.
     */
    fun toVideos(streams: List<StremioStream>): List<Video> = streams.mapNotNull(::toVideo)

    /**
     * Subtitle files from an addon, as tracks the player can offer.
     *
     * Providers are generous to a fault: ask OpenSubtitles for one episode and it will happily
     * return sixty files, most of them the same language re-uploaded. So identical URLs collapse,
     * and each language is capped — past a few options per language nobody is choosing, they are
     * scrolling.
     *
     * The language is shown as a name rather than a code. Providers speak ISO 639-2 (`ara`, `eng`,
     * `spa`), which is correct and unreadable; a picker listing "ara" is asking the person to know
     * the standard. Anything the platform cannot name keeps its code, which is still better than
     * inventing one.
     */
    fun toTracks(subtitles: List<StremioSubtitle>): List<Track> {
        val seen = mutableSetOf<String>()
        val perLanguage = mutableMapOf<String, Int>()
        val tracks = mutableListOf<Track>()
        for (subtitle in subtitles) {
            if (subtitle.url.isBlank() || !seen.add(subtitle.url)) continue
            val code = subtitle.lang.trim().lowercase().ifEmpty { UNKNOWN_LANGUAGE }
            val taken = perLanguage.getOrDefault(code, 0)
            if (taken >= MAX_SUBTITLES_PER_LANGUAGE) continue
            perLanguage[code] = taken + 1
            // Numbered only past the first, so a language with one file reads as a language rather
            // than as the first of a series.
            val name = languageName(code).let { if (taken == 0) it else "$it ${taken + 1}" }
            tracks += Track(subtitle.url, name)
        }
        return tracks
    }

    private fun languageName(code: String): String = runCatching {
        Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && !it.equals(code, true) }
    }.getOrNull() ?: code

    private fun toVideo(stream: StremioStream): Video? {
        val label = streamLabel(stream)
        val videoUrl = when {
            !stream.url.isNullOrBlank() -> stream.url
            !stream.infoHash.isNullOrBlank() -> magnetOf(stream, label)
            else -> return null
        }
        return Video(
            videoUrl = videoUrl,
            videoTitle = label,
            resolution = parseResolution(label),
            headers = stream.behaviorHints?.proxyHeaders?.request
                ?.takeIf { it.isNotEmpty() }
                ?.let { headers -> Headers.Builder().apply { headers.forEach { add(it.key, it.value) } }.build() },
            subtitleTracks = stream.subtitles
                .filter { it.url.isNotBlank() }
                .map { Track(it.url, it.lang.ifBlank { UNKNOWN_LANGUAGE }) },
        )
    }

    /**
     * A torrent stream spelled the way the player already reads torrents.
     *
     * The player looks for `index=` inside a magnet link to know which file in the torrent to
     * open, which is exactly what Stremio's `fileIdx` says — so the two line up with no
     * negotiation. Trackers ride along from `sources`; the `dht:` entries there are peer hints
     * the torrent server finds on its own, so they are dropped rather than passed as trackers.
     */
    private fun magnetOf(stream: StremioStream, label: String): String = buildString {
        append("magnet:?xt=urn:btih:").append(stream.infoHash)
        append("&dn=").append(label.take(MAGNET_NAME_LIMIT).encodeUriComponent())
        stream.sources
            .filter { it.startsWith(TRACKER_PREFIX) }
            .forEach { append("&tr=").append(it.removePrefix(TRACKER_PREFIX).encodeUriComponent()) }
        append("&index=").append(stream.fileIdx ?: 0)
    }

    /**
     * What the quality picker shows for a stream.
     *
     * Addons split the label over two fields and pad both with newlines and emoji — `name` holds
     * something short like the addon or resolution, `description` (or the older `title`) holds
     * the release name, seeders and size. Both matter when choosing, so both are kept, flattened
     * onto one line because the picker is one line tall.
     */
    private fun streamLabel(stream: StremioStream): String {
        val detail = stream.description ?: stream.title
        return listOfNotNull(stream.name, detail)
            .flatMap { it.split('\n') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifEmpty { UNTITLED_STREAM }
    }

    private fun parseResolution(label: String): Int? {
        FOUR_K_MARKERS.forEach { if (label.contains(it, ignoreCase = true)) return FOUR_K_HEIGHT }
        return RESOLUTION_PATTERN.find(label)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun episodeName(video: StremioVideo): String {
        val title = video.title ?: video.name
        val season = video.season
        val episode = video.episode
        val marker = when {
            season != null && episode != null -> "S%d:E%d".format(season, episode)
            episode != null -> "E$episode"
            else -> null
        }
        return listOfNotNull(marker, title?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifEmpty { UNTITLED_EPISODE }
    }

    private fun buildDescription(meta: StremioMeta): String? {
        val facts = listOfNotNull(
            meta.releaseInfo.primitiveText(),
            meta.runtime?.takeIf { it.isNotBlank() },
            meta.country?.takeIf { it.isNotBlank() },
        )
        return listOfNotNull(
            meta.description?.takeIf { it.isNotBlank() },
            facts.joinToString(" · ").takeIf { it.isNotEmpty() },
        ).joinToString("\n\n").takeIf { it.isNotEmpty() }
    }

    private fun statusOf(meta: StremioMeta): Int = when (meta.status?.lowercase()) {
        "continuing", "ongoing", "returning series" -> SAnime.ONGOING
        "ended", "completed", "canceled", "cancelled" -> SAnime.COMPLETED
        // No status field, but a year range with a closing year is the same statement.
        else -> if (meta.releaseInfo.primitiveText()?.matches(CLOSED_YEAR_RANGE) == true) {
            SAnime.COMPLETED
        } else {
            SAnime.UNKNOWN
        }
    }

    /**
     * `released` is documented as ISO 8601 and arrives as anything adjacent to it — a full
     * timestamp, a bare date, or a lone year. Each is tried in turn; an unreadable one costs the
     * episode its date and nothing more.
     */
    private fun parseReleaseDate(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        runCatching { return Instant.parse(value).toEpochMilli() }
        runCatching { return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val year = LEADING_YEAR.find(value)?.value?.toIntOrNull() ?: return 0L
        return runCatching {
            LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse { if (it is DateTimeParseException) 0L else throw it }
    }

    private const val SPECIALS_SEASON = 0
    private const val CAST_IN_ARTIST = 3
    private const val FOUR_K_HEIGHT = 2160
    private const val MAGNET_NAME_LIMIT = 80
    private const val TRACKER_PREFIX = "tracker:"
    private const val UNKNOWN_LANGUAGE = "und"
    private const val MAX_SUBTITLES_PER_LANGUAGE = 4
    private const val UNTITLED_STREAM = "Stream"
    private const val UNTITLED_EPISODE = "Episode"
    private const val DEFAULT_SINGLE_EPISODE_NAME = "Film"

    private val FOUR_K_MARKERS = listOf("2160p", "4k", "uhd")
    private val RESOLUTION_PATTERN = Regex("""(\d{3,4})\s*p\b""", RegexOption.IGNORE_CASE)
    private val CLOSED_YEAR_RANGE = Regex("""\d{4}\s*[-–]\s*\d{4}""")
    private val LEADING_YEAR = Regex("""\d{4}""")
}
