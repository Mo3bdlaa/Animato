package tachiyomi.domain.category.anime.interactor

import aniyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.domain.library.model.plus

class ResetAnimeCategoryFlags(
    private val preferences: AnimeLibraryPreferences,
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await() {
        val sort = preferences.animeSortingMode().get()
        categoryRepository.updateAllAnimeCategoryFlags(sort.type + sort.direction)
    }
}
