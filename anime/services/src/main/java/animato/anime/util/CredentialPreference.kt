package animato.anime.util

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * A preference holding something that must not leave the device by accident.
 *
 * ## What the prefix does, and why it is not decoration
 *
 * A backup takes every app preference there is — `preferenceStore.getAll()`, no allow-list — and
 * writes them into a file people share, sync and upload. The one thing that keeps a secret out of
 * that file is the `__PRIVATE_` prefix on its key, which the backup writer filters on unless
 * somebody explicitly ticks *private settings*.
 *
 * Three of this fork's stores were missing it: a Jellyfin access token, a Torznab API key and a
 * proxy password, all sitting in plain text in any ordinary backup. Nothing was leaking them on
 * purpose — the prefix is simply the whole mechanism, and it is invisible if you have not seen it
 * before. Which is why this exists rather than a `Preference.privateKey(...)` call at each site:
 * the name of the function is the reminder.
 *
 * ## Why moving the value has to happen here
 *
 * The prefix is part of the key, so adopting it renames the preference — and a renamed preference
 * is an empty one. Left alone, this change would silently sign everybody out of their own Jellyfin
 * server and drop their indexers, which is a worse outcome than the problem being fixed.
 *
 * So the old key is read once and carried over, then cleared. It runs on every construction and
 * costs one lookup of a key that is not there; expressing it as a migration step somewhere central
 * would put the fix a long way from the thing it is fixing, for a saving of nothing.
 */
fun PreferenceStore.credentialString(plainKey: String, default: String = ""): Preference<String> {
    val preference = getString(Preference.privateKey(plainKey), default)
    val legacy = getString(plainKey, default)
    if (legacy.isSet()) {
        // Only when the private side has nothing, so a restore of a private backup is never
        // overwritten by a stale plain value that a later restore happened to bring back.
        if (!preference.isSet()) {
            preference.set(legacy.get())
        }
        legacy.delete()
    }
    return preference
}
