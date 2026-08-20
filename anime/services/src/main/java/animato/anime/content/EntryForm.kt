package animato.anime.content

import eu.kanade.tachiyomi.animesource.AnimeSource

/**
 * How a title is put together — the second axis the app was missing.
 *
 * ## The problem it names
 *
 * Everything watchable is stored as an anime, because that is the half of the model that plays
 * video. That was true when the only watchable thing *was* anime. It now holds films, series,
 * live channels and anime, and each of them answers three questions differently:
 *
 * - Does progress through it mean anything?
 * - Does asking the source again ever return something new?
 * - Is "episodes" the right word for what is inside it?
 *
 * The app has been answering those by accident. A channel keeps no progress because a live stream
 * reports no duration and a guard in the player returns early; a film is one row because its
 * metadata happened to carry no videos. Both are right, and neither is *said* anywhere — so the
 * next thing that behaves like a film has to rediscover it.
 *
 * ## Why it is asked rather than stored
 *
 * No column and no migration. The answer is already in data every entry carries: an M3U source
 * holds nothing but channels, and a Stremio entry's url begins with the type the addon gave it.
 * Storing a copy would mean a copy that can disagree with the url beside it.
 *
 * The source is what answers, through [KnowsEntryForm]. A source that does not implement it has
 * serials, which is what every extension in the ecosystem actually has and what the app assumed
 * before this existed.
 */
enum class EntryForm {
    /** Episodes or chapters, arriving over time. Every extension, and a Stremio series. */
    Serial,

    /** One thing, complete. A film — where "episode 1 of 1" is a sentence about our data model. */
    Single,

    /**
     * On now, and only now.
     *
     * There is no position to be at and no end to reach, so progress, *seen*, and asking the
     * source for anything new are all meaningless rather than merely unused.
     */
    Live,
    ;

    /** Whether being part-way through it is a thing that can happen. */
    val hasProgress: Boolean get() = this != Live

    /** Whether re-asking the source can ever return something that was not there before. */
    val canGrow: Boolean get() = this == Serial
}

/**
 * A source that knows what shape its entries are.
 *
 * Implemented by the sources that carry more than one shape, or exactly one that is not a serial.
 * Everything else is left alone: an extension is a serial catalogue and has no reason to say so.
 */
interface KnowsEntryForm {
    /**
     * The form of the entry at [entryUrl].
     *
     * The url rather than the entry, because that is the one field guaranteed to be present and
     * correct at every point this gets asked — including for a row that has been saved to the
     * library but never fetched.
     */
    fun formOf(entryUrl: String): EntryForm
}

/**
 * What form an entry from this source has, asking the source only if it has an opinion.
 *
 * The single entry point, so that "a source that says nothing has serials" is written once instead
 * of at every call site — and so a stub source, which is what a missing extension becomes, answers
 * the same as the extension it stands in for rather than crashing the screen that asked.
 */
fun AnimeSource?.entryForm(entryUrl: String): EntryForm =
    (this as? KnowsEntryForm)?.formOf(entryUrl) ?: EntryForm.Serial
