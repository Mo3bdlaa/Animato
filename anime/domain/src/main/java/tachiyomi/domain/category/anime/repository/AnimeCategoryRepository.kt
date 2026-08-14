package tachiyomi.domain.category.anime.repository

import animato.domain.category.AnimeCategory
import animato.domain.category.AnimeCategoryUpdate
import kotlinx.coroutines.flow.Flow

interface AnimeCategoryRepository {

    suspend fun getAnimeCategory(id: Long): AnimeCategory?

    suspend fun getAllAnimeCategories(): List<AnimeCategory>

    suspend fun getAllVisibleAnimeCategories(): List<AnimeCategory>

    fun getAllAnimeCategoriesAsFlow(): Flow<List<AnimeCategory>>

    fun getAllVisibleAnimeCategoriesAsFlow(): Flow<List<AnimeCategory>>

    suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory>

    suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<AnimeCategory>

    fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>>

    fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>>

    suspend fun insertAnimeCategory(category: AnimeCategory)

    suspend fun updatePartialAnimeCategory(update: AnimeCategoryUpdate)

    suspend fun updatePartialAnimeCategories(updates: List<AnimeCategoryUpdate>)

    suspend fun updateAllAnimeCategoryFlags(flags: Long?)

    suspend fun deleteAnimeCategory(categoryId: Long)
}
