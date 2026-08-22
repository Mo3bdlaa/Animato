package animato.anime.util

import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.system.logcat

/**
 * Decode a preference holding a store's whole contents, without destroying it when it cannot be
 * read.
 *
 * ## The failure this exists to prevent
 *
 * Four stores in this app keep everything they hold as one JSON string under one key: Stremio
 * addons, M3U playlists, Jellyfin servers, Torznab indexers. Each of them decoded that string,
 * caught the failure, logged it, and carried on with an empty list — which reads as careful, and is
 * the first half of losing the data.
 *
 * The second half is the next write. The store now holds an empty list as its state; somebody adds
 * one addon; the store serialises *its state* back over the same key. The unreadable original is
 * gone, permanently, and what it contained was a person's Jellyfin access tokens, their Torznab API
 * keys, or a list of addons they had put together over months. The only trace of any of it was one
 * line in a log nobody reads.
 *
 * The trigger is not exotic, either. Adding a single non-optional field to a stored model makes
 * every existing record undecodable on the next release — an ordinary change, with no warning
 * attached, that would quietly empty everybody's servers on upgrade.
 *
 * ## What this does instead
 *
 * Before returning empty, it copies the raw text aside under a key nothing else writes. The data is
 * then still there: recoverable by hand, and recoverable by a later version taught to read the old
 * shape. Not silently gone.
 *
 * Salvaging at *read* time rather than flagging it for write time is deliberate — by the time a
 * write happens, the value in memory is already the empty one, and any bookkeeping that has to
 * survive from one to the other is one more thing to get wrong.
 *
 * The first salvage wins: a second failure means this has already failed once, and overwriting the
 * salvage with whatever followed it would lose the very thing being kept. The salvage key is
 * private-prefixed, so a rescued access token does not travel into an ordinary backup.
 */
inline fun <T> PreferenceStore.decodeOrSalvage(
    stored: Preference<String>,
    key: String,
    empty: T,
    decode: (String) -> T,
): T {
    val raw = stored.get()
    if (raw.isEmpty()) return empty
    return runCatching { decode(raw) }.getOrElse { error ->
        val salvage = getString(salvageKeyFor(key), "")
        if (!salvage.isSet()) salvage.set(raw)
        logcat(LogPriority.ERROR, error) {
            "Stored $key could not be read; the raw value is kept at ${salvageKeyFor(key)}"
        }
        empty
    }
}

/** Where an unreadable value is kept. Private, so a salvaged credential stays out of backups. */
fun salvageKeyFor(key: String): String = Preference.privateKey("${key}__unreadable")
