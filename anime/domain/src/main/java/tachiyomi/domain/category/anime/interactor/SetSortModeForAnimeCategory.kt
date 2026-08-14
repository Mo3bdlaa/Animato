package tachiyomi.domain.category.anime.interactor

import animato.domain.category.AnimeCategory
import animato.domain.category.AnimeCategoryUpdate
import aniyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.LibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCategory(
    private val preferences: LibraryPreferences,
    private val animePreferences: AnimeLibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(
        categoryId: Long?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        val category = categoryId?.let { categoryRepository.getAnimeCategory(it) }
        val flags = (category?.flags ?: 0) + type + direction
        if (type == AnimeLibrarySort.Type.Random) {
            animePreferences.randomAnimeSortSeed().set(Random.nextInt())
        }
        if (category != null && preferences.categorizedDisplaySettings.get()) {
            categoryRepository.updatePartialAnimeCategory(
                AnimeCategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            animePreferences.animeSortingMode().set(AnimeLibrarySort(type, direction))
            categoryRepository.updateAllAnimeCategoryFlags(flags)
        }
    }

    suspend fun await(
        category: AnimeCategory?,
        type: AnimeLibrarySort.Type,
        direction: AnimeLibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }
}
