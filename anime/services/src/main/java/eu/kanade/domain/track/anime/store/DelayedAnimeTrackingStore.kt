package eu.kanade.domain.track.anime.store

import android.content.Context
import androidx.core.content.edit
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DelayedAnimeTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored.
     */
    private val preferences = context.getSharedPreferences("tracking_queue", Context.MODE_PRIVATE)

    fun addAnime(trackId: Long, lastEpisodeSeen: Double) {
        val previousLastEpisodeSeen = preferences.getFloat(trackId.toString(), 0f)
        if (lastEpisodeSeen > previousLastEpisodeSeen) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $trackId, last episode seen: $lastEpisodeSeen" }
            preferences.edit {
                putFloat(trackId.toString(), lastEpisodeSeen.toFloat())
            }
        }
    }

    fun removeAnimeItem(trackId: Long) {
        preferences.edit {
            remove(trackId.toString())
        }
    }

    fun getAnimeItems(): List<DelayedAnimeTrackingItem> {
        // Actually nullable now. `mapNotNull` sat over a lambda that could never return null, on
        // two unchecked parses of a persisted preferences file — so one stray key threw out of the
        // queue read and disabled offline tracking permanently, with nothing said anywhere.
        return preferences.all.mapNotNull {
            val trackId = it.key.toLongOrNull() ?: return@mapNotNull null
            val seen = it.value?.toString()?.toFloatOrNull() ?: return@mapNotNull null
            DelayedAnimeTrackingItem(trackId = trackId, lastEpisodeSeen = seen)
        }
    }

    data class DelayedAnimeTrackingItem(
        val trackId: Long,
        val lastEpisodeSeen: Float,
    )
}
