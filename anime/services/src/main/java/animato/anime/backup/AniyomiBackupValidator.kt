package animato.anime.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Reads a backup and reports what will not come across.
 *
 * This runs before the restore so that the missing pieces can be named up front. A source whose
 * extension is not installed still restores every entry that came from it — the entries are simply
 * unreadable until the extension is back, and it is far better to say so first than to leave the
 * user to work it out from a library that will not open.
 */
class AniyomiBackupValidator(
    private val context: Context,
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: SourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {

    fun validate(uri: Uri): Results {
        val backup = AniyomiBackupDecoder(context).decode(uri)

        val missingAnimeSources = backup.animeSources
            .filter { animeSourceManager.get(it.sourceId) == null }
            .map { it.name.ifBlank { it.sourceId.toString() } }

        val missingMangaSources = backup.mangaSources
            .filter { mangaSourceManager.get(it.sourceId) == null }
            .map { it.name.ifBlank { it.sourceId.toString() } }

        // A tracker id the backup uses that this app has no tracker for. Aniyomi shipped anime-only
        // trackers that are not here yet, and their entries are the ones this catches.
        val missingTrackers = (
            backup.anime.flatMap { it.tracking }.map { it.syncId.toLong() } +
                backup.manga.flatMap { it.tracking }.map { it.syncId.toLong() }
            )
            .distinct()
            .mapNotNull { trackerManager.get(it) }
            .filterNot { it.isLoggedIn }
            .map { it.name }

        return Results(
            missingSources = (missingAnimeSources + missingMangaSources).distinct().sorted(),
            missingTrackers = missingTrackers.distinct().sorted(),
            animeCount = backup.anime.size,
            mangaCount = backup.manga.size,
        )
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
        val animeCount: Int,
        val mangaCount: Int,
    )
}
