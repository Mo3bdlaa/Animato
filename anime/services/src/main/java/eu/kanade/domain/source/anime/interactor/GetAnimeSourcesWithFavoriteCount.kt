package eu.kanade.domain.source.anime.interactor

import aniyomi.domain.source.interactor.SetAnimeMigrateSorting
import aniyomi.domain.source.service.AnimeSourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import java.util.Collections

class GetAnimeSourcesWithFavoriteCount(
    private val repository: AnimeSourceRepository,
    private val preferences: AnimeSourcePreferences,
) {

    fun subscribe(): Flow<List<Pair<AnimeSource, Long>>> {
        return combine(
            preferences.migrationSortingDirection().changes(),
            preferences.migrationSortingMode().changes(),
            repository.getAnimeSourcesWithFavoriteCount(),
        ) { direction, mode, list ->
            list
                .filterNot { it.first.id == LocalAnimeSource.ID }
                .sortedWith(sortFn(direction, mode))
        }
    }

    private fun sortFn(
        direction: SetAnimeMigrateSorting.Direction,
        sorting: SetAnimeMigrateSorting.Mode,
    ): java.util.Comparator<Pair<AnimeSource, Long>> {
        val sortFn: (Pair<AnimeSource, Long>, Pair<AnimeSource, Long>) -> Int = { a, b ->
            when (sorting) {
                SetAnimeMigrateSorting.Mode.ALPHABETICAL -> {
                    when {
                        a.first.isStub && !b.first.isStub -> -1
                        b.first.isStub && !a.first.isStub -> 1
                        else -> a.first.name.lowercase().compareToWithCollator(b.first.name.lowercase())
                    }
                }
                SetAnimeMigrateSorting.Mode.TOTAL -> {
                    when {
                        a.first.isStub && !b.first.isStub -> -1
                        b.first.isStub && !a.first.isStub -> 1
                        else -> a.second.compareTo(b.second)
                    }
                }
            }
        }

        return when (direction) {
            SetAnimeMigrateSorting.Direction.ASCENDING -> Comparator(sortFn)
            SetAnimeMigrateSorting.Direction.DESCENDING -> Collections.reverseOrder(sortFn)
        }
    }
}
