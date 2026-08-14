package tachiyomi.data.category.anime

import animato.domain.category.AnimeCategory
import animato.domain.category.AnimeCategoryUpdate
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository
import tachiyomi.mi.data.AnimeDatabase

class AnimeCategoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeCategoryRepository {

    override suspend fun getAnimeCategory(id: Long): AnimeCategory? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCategory) }
    }

    override suspend fun getAllAnimeCategories(): List<AnimeCategory> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCategory) }
    }

    override suspend fun getAllVisibleAnimeCategories(): List<AnimeCategory> {
        return handler.awaitList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override fun getAllAnimeCategoriesAsFlow(): Flow<List<AnimeCategory>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCategory) }
    }

    override fun getAllVisibleAnimeCategoriesAsFlow(): Flow<List<AnimeCategory>> {
        return handler.subscribeToList { categoriesQueries.getVisibleCategories(::mapCategory) }
    }

    override suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<AnimeCategory> {
        return handler.awaitList {
            categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCategory)
        }
    }

    override suspend fun insertAnimeCategory(category: AnimeCategory) {
        handler.await {
            categoriesQueries.insert(
                name = category.name,
                order = category.order,
                flags = category.flags,
            )
        }
    }

    override suspend fun updatePartialAnimeCategory(update: AnimeCategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialAnimeCategories(updates: List<AnimeCategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun AnimeDatabase.updatePartialBlocking(update: AnimeCategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllAnimeCategoryFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteAnimeCategory(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }

    private fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): AnimeCategory {
        return AnimeCategory(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
