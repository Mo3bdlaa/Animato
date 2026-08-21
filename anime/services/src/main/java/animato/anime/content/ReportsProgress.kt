package animato.anime.content

import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * A source that keeps its own record of what has been watched.
 *
 * ## Why only some sources
 *
 * Almost none can. A scraper extension reads somebody else's website and has no account there; a
 * Stremio addon answers questions and stores nothing; a playlist is a file. For all of those, this
 * app's database is the only record there is and there is nothing to tell.
 *
 * A media server is the exception, and the exception matters: it is the one kind of source that
 * already knows where you stopped, because you also watch it on a television. Without this, the two
 * halves of that drift apart immediately — the app knows about the phone and the server knows about
 * everything else, and neither is right.
 */
interface ReportsProgress {
    /**
     * Tell the source how far through [episodeUrl] the viewer is.
     *
     * Called while playing, not once at the end: a session that is killed by the system — which is
     * what closing a video app usually is — would otherwise report nothing at all, and the position
     * people most want carried across is the one from the episode they walked away from.
     *
     * Failure is silence. Progress reporting is a courtesy to a second device, and an error banner
     * over a playing video about a request the viewer did not make is worse than the drift.
     */
    suspend fun reportProgress(episodeUrl: String, positionMs: Long, durationMs: Long)

    /** Tell the source it was watched to the end. */
    suspend fun reportFinished(episodeUrl: String)
}

/**
 * What a source knew about an episode before this app ever saw it.
 *
 * Carried in [eu.kanade.tachiyomi.animesource.model.SEpisode.memo], which is the field the source
 * API provides for exactly this — extra data an app defines for itself, that survives into the
 * database and back out.
 *
 * Applied **only to an episode the app has never stored**. A server's record is authoritative right
 * up until this app has one of its own, and after that it is a second opinion arriving late: a
 * refresh that overwrote local progress with a server value from before the last episode would undo
 * watching, which is the one thing this must never do.
 */
object SourceProgress {

    /** Whether the source considers this watched. */
    const val SEEN = "animato.seen"

    /** How far into it the source thinks the viewer got, in milliseconds. */
    const val POSITION_MS = "animato.positionMs"

    fun memoOf(seen: Boolean, positionMs: Long): JsonObject = JsonObject(
        buildMap {
            if (seen) put(SEEN, JsonPrimitive(true))
            if (positionMs > 0) put(POSITION_MS, JsonPrimitive(positionMs))
        },
    )

    fun seenIn(memo: JsonObject): Boolean =
        runCatching { memo[SEEN]?.jsonPrimitive?.boolean }.getOrNull() == true

    fun positionIn(memo: JsonObject): Long =
        runCatching { memo[POSITION_MS]?.jsonPrimitive?.long }.getOrNull() ?: 0L
}

/** The source's progress reporter, or null if it does not keep one. */
fun AnimeSource?.asProgressReporter(): ReportsProgress? = this as? ReportsProgress
