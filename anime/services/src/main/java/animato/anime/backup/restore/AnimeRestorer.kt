package animato.anime.backup.restore

import animato.anime.backup.models.BackupAnime
import animato.anime.backup.models.BackupAnimeHistory
import animato.anime.backup.models.BackupAnimeTracking
import animato.anime.backup.models.BackupEpisode
import animato.data.AnimeUpdateStrategyColumnAdapter
import animato.data.FetchTypeColumnAdapter
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

/**
 * Puts one anime from a backup into the library, with its episodes, history, tracks and seasons.
 *
 * An entry already in the library is merged rather than replaced: whichever side has the higher
 * version wins on the details, and progress is taken as the furthest of the two. Restoring is
 * therefore safe to repeat, and safe to do onto a library that is not empty.
 */
class AnimeRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val insertTrack: InsertAnimeTrack = Injekt.get(),
    fetchInterval: AnimeFetchInterval = Injekt.get(),
) {

    private val now = ZonedDateTime.now()
    private val currentFetchWindow = fetchInterval.getWindow(now)

    /**
     * The backup's entries, entries the library does not have yet first.
     *
     * A restore that runs out of time or is cancelled halfway has then brought across what the
     * library was missing, rather than half of what it already had.
     */
    suspend fun sortByNew(backupAnime: List<BackupAnime>): List<BackupAnime> {
        val urlsBySource = handler.awaitList { animesQueries.getAllAnimeSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupAnime.sortedWith(
            compareBy<BackupAnime> { it.url in urlsBySource[it.source].orEmpty() }
                .then(compareByDescending { it.lastModifiedAt }),
        )
    }

    /**
     * Restores [backupAnime] and the [seasons] belonging to it.
     *
     * Seasons are restored through their parent rather than on their own because the link between
     * them is an id, and the id in the backup belongs to the backup. Only once the parent has a row
     * here is there a number worth writing into a season.
     */
    suspend fun restore(
        backupAnime: BackupAnime,
        backupCategories: List<BackupCategory>,
        seasons: List<BackupAnime> = emptyList(),
    ) {
        handler.await(inTransaction = true) {
            val restored = restoreAnime(backupAnime, parentId = null)

            seasons.forEach { season ->
                val restoredSeason = restoreAnime(season, parentId = restored.id)
                restoreDetails(restoredSeason, season, backupCategories)
            }

            restoreDetails(restored, backupAnime, backupCategories)
        }
    }

    private suspend fun restoreAnime(backupAnime: BackupAnime, parentId: Long?): Anime {
        val anime = backupAnime.toAnime().copy(parentId = parentId)
        val dbAnime = getAnimeByUrlAndSourceId.await(backupAnime.url, backupAnime.source)
        return if (dbAnime == null) {
            insert(anime.copy(initialized = anime.description != null))
        } else {
            val merged = if (anime.version > dbAnime.version) {
                dbAnime.mergeWith(anime)
            } else {
                anime.mergeWith(dbAnime)
            }
            update(merged.copy(id = dbAnime.id, parentId = parentId ?: dbAnime.parentId))
        }
    }

    private suspend fun restoreDetails(
        anime: Anime,
        backupAnime: BackupAnime,
        backupCategories: List<BackupCategory>,
    ) {
        restoreCategories(anime, backupAnime.categories, backupCategories)
        restoreEpisodes(anime, backupAnime.episodes)
        restoreTracking(anime, backupAnime.tracking)
        restoreHistory(backupAnime.history)
        updateAnime.awaitUpdateFetchInterval(anime, now, currentFetchWindow)
    }

    /**
     * This anime with the parts of [newer] that describe the same thing more recently.
     *
     * Favourite and initialized are held rather than taken: an entry in the library does not stop
     * being one because the backup was made before it was added.
     */
    private fun Anime.mergeWith(newer: Anime): Anime {
        return copy(
            favorite = favorite || newer.favorite,
            author = newer.author,
            artist = newer.artist,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            backgroundUrl = newer.backgroundUrl,
            status = newer.status,
            initialized = initialized || newer.initialized,
            version = newer.version,
            fetchType = newer.fetchType,
        )
    }

    private suspend fun insert(anime: Anime): Anime {
        val id = handler.awaitOneExecutable(inTransaction = true) {
            animesQueries.insert(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre,
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = anime.initialized,
                viewerFlags = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                updateStrategy = anime.updateStrategy,
                version = anime.version,
                fetchType = anime.fetchType,
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
                backgroundUrl = anime.backgroundUrl,
                backgroundLastModified = anime.backgroundLastModified,
                memo = anime.memo,
            )
            animesQueries.selectLastInsertedRowId()
        }
        return anime.copy(id = id)
    }

    private suspend fun update(anime: Anime): Anime {
        handler.await(inTransaction = true) {
            animesQueries.update(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre?.joinToString(separator = ", "),
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = anime.initialized,
                viewer = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                animeId = anime.id,
                updateStrategy = AnimeUpdateStrategyColumnAdapter.encode(anime.updateStrategy),
                version = anime.version,
                // The restore is the writer here, so the row is not a source sync and must not be
                // counted as one by whatever watches this column.
                isSyncing = 1,
                fetchType = FetchTypeColumnAdapter.encode(anime.fetchType),
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
                backgroundUrl = anime.backgroundUrl,
                backgroundLastModified = anime.backgroundLastModified,
                memo = MemoColumnAdapter.encode(anime.memo),
            )
        }
        return anime
    }

    private suspend fun restoreEpisodes(anime: Anime, backupEpisodes: List<BackupEpisode>) {
        if (backupEpisodes.isEmpty()) return

        val dbEpisodesByUrl = getEpisodesByAnimeId.await(anime.id).associateBy { it.url }

        val (existing, new) = backupEpisodes
            .mapNotNull { backupEpisode ->
                val episode = backupEpisode.toEpisode().copy(animeId = anime.id)
                val dbEpisode = dbEpisodesByUrl[episode.url] ?: return@mapNotNull episode

                if (episode.forComparison() == dbEpisode.forComparison()) return@mapNotNull null

                var updated = episode
                    .copyFrom(dbEpisode)
                    .copy(
                        id = dbEpisode.id,
                        bookmark = episode.bookmark || dbEpisode.bookmark,
                        fillermark = episode.fillermark || dbEpisode.fillermark,
                    )
                // Watched never becomes unwatched, and a position already further in is kept.
                if (dbEpisode.seen && !updated.seen) {
                    updated = updated.copy(seen = true, lastSecondSeen = dbEpisode.lastSecondSeen)
                } else if (updated.lastSecondSeen == 0L && dbEpisode.lastSecondSeen != 0L) {
                    updated = updated.copy(lastSecondSeen = dbEpisode.lastSecondSeen)
                }
                updated
            }
            .partition { it.id > 0 }

        insertEpisodes(new)
        updateEpisodes(existing)
    }

    private fun Episode.forComparison() =
        copy(id = 0L, animeId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun insertEpisodes(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        handler.await(inTransaction = true) {
            episodes.forEach { episode ->
                episodesQueries.insert(
                    animeId = episode.animeId,
                    url = episode.url,
                    name = episode.name,
                    scanlator = episode.scanlator,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    lastSecondSeen = episode.lastSecondSeen,
                    totalSeconds = episode.totalSeconds,
                    episodeNumber = episode.episodeNumber,
                    sourceOrder = episode.sourceOrder,
                    dateFetch = episode.dateFetch,
                    dateUpload = episode.dateUpload,
                    version = episode.version,
                    summary = episode.summary,
                    previewUrl = episode.previewUrl,
                    fillermark = episode.fillermark,
                    memo = episode.memo,
                )
            }
        }
    }

    private suspend fun updateEpisodes(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        handler.await(inTransaction = true) {
            episodes.forEach { episode ->
                episodesQueries.update(
                    animeId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    summary = null,
                    previewUrl = null,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastSecondSeen = episode.lastSecondSeen,
                    totalSeconds = episode.totalSeconds,
                    episodeNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    episodeId = episode.id,
                    version = episode.version,
                    isSyncing = 0,
                    memo = MemoColumnAdapter.encode(episode.memo),
                )
            }
        }
    }

    /**
     * Puts the anime back in the categories it was in.
     *
     * The backup refers to a category by its order, and categories are matched here by name,
     * because an order is only meaningful inside the backup it came from.
     */
    private suspend fun restoreCategories(
        anime: Anime,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        if (categories.isEmpty()) return

        val dbCategoriesByName = getCategories.await().associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val categoryIds = categories.mapNotNull { order ->
            backupCategoriesByOrder[order]
                ?.let { dbCategoriesByName[it.name] }
                ?.id
        }
        if (categoryIds.isEmpty()) return

        handler.await(inTransaction = true) {
            animes_categoriesQueries.deleteAnimeCategoryByAnimeId(anime.id)
            categoryIds.forEach { categoryId ->
                animes_categoriesQueries.insert(anime.id, categoryId)
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupAnimeHistory>) {
        if (backupHistory.isEmpty()) return

        val toUpdate = backupHistory.mapNotNull { entry ->
            val item = entry.toHistory()
            val dbHistory = handler.awaitOneOrNull {
                animehistoryQueries.getHistoryByEpisodeUrl(entry.url)
            }

            if (dbHistory == null) {
                // An episode the backup watched but this library does not have: nothing to hang
                // the history on, so it is dropped rather than invented.
                val episode = handler.awaitOneOrNull { episodesQueries.getEpisodeByUrl(entry.url) }
                return@mapNotNull episode?.let { item.copy(episodeId = it._id) }
            }

            item.copy(
                id = dbHistory._id,
                episodeId = dbHistory.episode_id,
                seenAt = max(item.seenAt?.time ?: 0L, dbHistory.last_seen?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let(::Date),
            )
        }
        if (toUpdate.isEmpty()) return

        handler.await(inTransaction = true) {
            toUpdate.forEach { animehistoryQueries.upsert(it.episodeId, it.seenAt) }
        }
    }

    private suspend fun restoreTracking(anime: Anime, backupTracks: List<BackupAnimeTracking>) {
        if (backupTracks.isEmpty()) return

        val dbTrackByTrackerId = getTracks.await(anime.id).associateBy { it.trackerId }

        val (existing, new) = backupTracks
            .mapNotNull {
                val track = it.toTrack()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: return@mapNotNull track.copy(id = 0, animeId = anime.id)

                if (track.forComparison() == dbTrack.forComparison()) return@mapNotNull null

                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastEpisodeSeen = max(dbTrack.lastEpisodeSeen, track.lastEpisodeSeen),
                )
            }
            .partition { it.id > 0 }

        if (new.isNotEmpty()) {
            insertTrack.awaitAll(new)
        }
        if (existing.isNotEmpty()) {
            handler.await(inTransaction = true) {
                existing.forEach { track ->
                    anime_syncQueries.update(
                        animeId = track.animeId,
                        syncId = track.trackerId,
                        mediaId = track.remoteId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastEpisodeSeen = track.lastEpisodeSeen,
                        totalEpisodes = track.totalEpisodes,
                        status = track.status,
                        score = track.score,
                        trackingUrl = track.remoteUrl,
                        startDate = track.startDate,
                        finishDate = track.finishDate,
                        private = track.private,
                        id = track.id,
                    )
                }
            }
        }
    }

    private fun AnimeTrack.forComparison() = copy(id = 0L, animeId = 0L)
}
