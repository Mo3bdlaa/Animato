package tachiyomi.domain.category.anime.interactor

import animato.domain.category.AnimeCategory
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

class GetVisibleAnimeCategories(
    private val categoryRepository: AnimeCategoryRepository,
) {
    fun subscribe(): Flow<List<AnimeCategory>> {
        return categoryRepository.getAllVisibleAnimeCategoriesAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<AnimeCategory>> {
        return categoryRepository.getVisibleCategoriesByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<AnimeCategory> {
        return categoryRepository.getAllVisibleAnimeCategories()
    }

    suspend fun await(animeId: Long): List<AnimeCategory> {
        return categoryRepository.getVisibleCategoriesByAnimeId(animeId)
    }
}
