package animato.anime.backup.create

import animato.anime.backup.models.BackupAnime
import animato.anime.backup.models.BackupAnimeHistory
import animato.anime.backup.models.backupAnimeTrackMapper
import animato.anime.backup.models.backupEpisodeMapper
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Writes the anime library into the shape a backup stores it in.
 *
 * Each part is separately optional because a backup taken to move settings between devices has no
 * business carrying a thousand episode rows.
 */
class AnimeBackupCreator(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getHistory: GetAnimeHistory = Injekt.get(),
) {

    suspend operator fun invoke(anime: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return anime.map { backupAnime(it, options) }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        var backup = anime.toBackupAnime()

        // `chapters` is Mihon's name for the option; here it means episodes. One checkbox governs
        // both halves because nobody wants their episodes and their chapters decided separately.
        if (options.chapters) {
            val episodes = handler.awaitList {
                episodesQueries.getEpisodesByAnimeId(animeId = anime.id, mapper = backupEpisodeMapper)
            }
            if (episodes.isNotEmpty()) backup = backup.copy(episodes = episodes)
        }

        if (options.categories) {
            // By order, not by id: an id means nothing outside the database it came from, and the
            // restorer matches these back up by the name the order points at.
            val categories = getCategories.await(anime.id).map { it.order }
            if (categories.isNotEmpty()) backup = backup.copy(categories = categories)
        }

        if (options.tracking) {
            val tracks = handler.awaitList {
                anime_syncQueries.getTracksByAnimeId(anime.id, backupAnimeTrackMapper)
            }
            if (tracks.isNotEmpty()) backup = backup.copy(tracking = tracks)
        }

        if (options.history) {
            val history = getHistory.await(anime.id).mapNotNull { entry ->
                val episode = handler.awaitOneOrNull { episodesQueries.getEpisodeById(entry.episodeId) }
                episode?.let { BackupAnimeHistory(url = it.url, lastRead = entry.seenAt?.time ?: 0L) }
            }
            if (history.isNotEmpty()) backup = backup.copy(history = history)
        }

        return backup
    }
}

/**
 * The anime itself, without any of the rows that hang off it.
 *
 * The database id is written, which nothing else in a backup does. It is the only way a season can
 * say which entry it belongs to, and the restorer uses it for that and throws it away after.
 */
private fun Anime.toBackupAnime() = BackupAnime(
    source = source,
    url = url,
    title = title,
    artist = artist,
    author = author,
    description = description,
    genre = genre.orEmpty(),
    status = status.toInt(),
    thumbnailUrl = thumbnailUrl,
    dateAdded = dateAdded,
    favorite = favorite,
    episodeFlags = episodeFlags.toInt(),
    viewerFlags = viewerFlags.toInt(),
    updateStrategy = updateStrategy,
    lastModifiedAt = lastModifiedAt,
    favoriteModifiedAt = favoriteModifiedAt,
    version = version,
    memo = MemoColumnAdapter.encode(memo),
    backgroundUrl = backgroundUrl,
    parentId = parentId,
    id = id,
    seasonFlags = seasonFlags,
    seasonNumber = seasonNumber,
    seasonSourceOrder = seasonSourceOrder,
    fetchType = fetchType,
)
