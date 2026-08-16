package animato.anime.player

import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The quality a person last chose by hand, per anime.
 *
 * Without this the player picks again from scratch on every episode: an extension marks one video
 * `preferred` from a global quality setting, and that is what plays. Choose 720p because 1080p
 * stutters on this particular source, and the next episode is 1080p again. Reported upstream as a
 * daily annoyance, and confirmed here rather than assumed — nothing persisted a per-anime choice.
 *
 * ## Why resolution, and not an index
 *
 * The player identifies a video by `(hosterIndex, videoIndex)`, and neither survives an episode:
 * hosters come back in a different order, lists are different lengths, and a source may serve a
 * different set entirely. [Video.resolution] is the one field that means the same thing next time.
 *
 * A remembered resolution is a *preference*, not an instruction. If nothing on the next episode
 * offers it, the extension's own choice stands — which is the right failure, because a source that
 * has no 1080p for this episode should play something rather than nothing.
 *
 * ## Why a preference and not a column
 *
 * A column on `animes` would be a schema migration and a backup field for something that is neither
 * user data nor worth restoring onto another device: it describes how one source behaved on one
 * phone. A bounded map in a preference is the right size for that, and losing it costs one tap.
 */
class RememberedQuality(preferenceStore: PreferenceStore) {

    private val stored: Preference<Map<Long, Int>> = preferenceStore.getObjectFromString(
        key = "player_remembered_quality",
        defaultValue = emptyMap(),
        serializer = ::serialize,
        deserializer = ::deserialize,
    )

    /** The resolution last chosen for [animeId], or null if nothing was ever chosen. */
    fun get(animeId: Long): Int? = stored.get()[animeId]

    /**
     * Remembers the resolution of a video someone picked themselves.
     *
     * Videos with no resolution are ignored rather than stored as a null: a source that does not
     * report one cannot be matched against next time either, so there is nothing to remember.
     */
    fun set(animeId: Long, video: Video) {
        val resolution = video.resolution ?: return
        stored.set((stored.get() + (animeId to resolution)).takeNewest())
    }

    fun forget(animeId: Long) {
        stored.set(stored.get() - animeId)
    }

    /**
     * Keeps the map from growing without limit across a library of thousands.
     *
     * Insertion order is what `LinkedHashMap` preserves and what the serialised form round-trips, so
     * "newest" is the tail. Dropping the head loses the least recently *set* entry, which is not
     * quite least recently used — and is close enough for something whose loss costs one tap.
     */
    private fun Map<Long, Int>.takeNewest(): Map<Long, Int> =
        if (size <= MAX_ENTRIES) this else entries.drop(size - MAX_ENTRIES).associate { it.toPair() }

    internal companion object {
        const val MAX_ENTRIES = 500

        /**
         * `id:resolution` pairs separated by commas. A preference store holds strings, and this is
         * small enough that a serialisation library would be the heavier answer.
         */
        internal fun serialize(value: Map<Long, Int>): String =
            value.entries.joinToString(",") { "${it.key}:${it.value}" }

        internal fun deserialize(value: String): Map<Long, Int> = value
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val resolution = entry.substringAfter(':', "").toIntOrNull() ?: return@mapNotNull null
                id to resolution
            }
            .toMap()
    }
}
