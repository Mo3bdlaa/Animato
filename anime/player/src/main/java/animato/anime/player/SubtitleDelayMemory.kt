package animato.anime.player

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The subtitle offset a person settled on, per anime.
 *
 * Subtitles being out of sync is not a per-episode accident. A release is encoded for one cut and a
 * subtitle file is timed for another, and the gap is the same two seconds on every episode of the
 * season — so correcting it per episode means correcting somebody else's mistake twelve times. The
 * panel that fixes it already existed and worked; what it lacked was memory.
 *
 * Per anime rather than one number for everything. The global default in the player settings
 * answers a different question, and a library where one show needs +2s and nothing else needs any
 * is the ordinary case that a single value cannot hold.
 *
 * The same shape as [RememberedQuality] and for the same reasons: a preference rather than a
 * column, because this describes how one subtitle file behaved on one phone rather than anything
 * worth migrating or restoring elsewhere, and bounded, because a library runs to thousands.
 */
class SubtitleDelayMemory(preferenceStore: PreferenceStore) {

    private val stored: Preference<Map<Long, Int>> = preferenceStore.getObjectFromString(
        key = "player_subtitle_delay_per_anime",
        defaultValue = emptyMap(),
        serializer = ::serialize,
        deserializer = ::deserialize,
    )

    /** The offset in milliseconds last set for [animeId], or null if it never needed one. */
    fun get(animeId: Long?): Int? = animeId?.let { stored.get()[it] }

    /**
     * Zero forgets rather than stores.
     *
     * An offset of zero is the absence of an offset. Keeping it would mean a record for every anime
     * whose delay panel was ever opened, all of them saying nothing.
     */
    fun set(animeId: Long?, delayMillis: Int) {
        val id = animeId ?: return
        stored.set(
            if (delayMillis == 0) stored.get() - id else (stored.get() + (id to delayMillis)).takeNewest(),
        )
    }

    /** See [RememberedQuality.takeNewest] — same bound, same reasoning, same acceptable imprecision. */
    private fun Map<Long, Int>.takeNewest(): Map<Long, Int> =
        if (size <= MAX_ENTRIES) this else entries.drop(size - MAX_ENTRIES).associate { it.toPair() }

    internal companion object {
        const val MAX_ENTRIES = 500

        /** `id:millis` pairs separated by commas, as the quality memory next door does. */
        internal fun serialize(value: Map<Long, Int>): String =
            value.entries.joinToString(",") { "${it.key}:${it.value}" }

        internal fun deserialize(value: String): Map<Long, Int> = value
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val id = entry.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
                val delay = entry.substringAfter(':', "").toIntOrNull() ?: return@mapNotNull null
                id to delay
            }
            .toMap()
    }
}
