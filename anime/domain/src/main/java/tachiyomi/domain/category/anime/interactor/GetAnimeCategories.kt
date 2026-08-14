package tachiyomi.domain.category.anime.interactor

import animato.domain.category.AnimeCategory
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

class GetAnimeCategories(
    private val categoryRepository: AnimeCategoryRepository,
) {

    fun subscribe(): Flow<List<AnimeCategory>> {
        return categoryRepository.getAllAnimeCategoriesAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<AnimeCategory>> {
        return categoryRepository.getCategoriesByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<AnimeCategory> {
        return categoryRepository.getAllAnimeCategories()
    }

    suspend fun await(animeId: Long): List<AnimeCategory> {
        return categoryRepository.getCategoriesByAnimeId(animeId)
    }
}
