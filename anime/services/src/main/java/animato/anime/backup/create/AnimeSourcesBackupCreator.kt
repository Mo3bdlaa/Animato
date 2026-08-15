package animato.anime.backup.create

import animato.anime.backup.models.BackupAnime
import animato.anime.backup.models.BackupAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Writes the name of every source the backup's entries came from.
 *
 * Nothing restores from this. It is what lets the import screen say "AnimeSource" instead of
 * "1234567" when the extension is not installed on the other side.
 *
 * `getOrStub` rather than `get`: a source whose extension has since been uninstalled still has
 * entries in the backup, and its name is exactly the thing worth remembering.
 */
class AnimeSourcesBackupCreator(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) {

    operator fun invoke(anime: List<BackupAnime>): List<BackupAnimeSource> {
        return anime.asSequence()
            .map(BackupAnime::source)
            .distinct()
            .map(animeSourceManager::getOrStub)
            .map { BackupAnimeSource(name = it.name, sourceId = it.id) }
            .toList()
    }
}
