package tachiyomi.domain.category.anime.interactor

import animato.domain.category.AnimeCategory
import animato.domain.category.AnimeCategoryUpdate
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

class HideAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(category: AnimeCategory) = withNonCancellableContext {
        val update = AnimeCategoryUpdate(
            id = category.id,
            hidden = !category.hidden,
        )

        try {
            categoryRepository.updatePartialAnimeCategory(update)
            RenameAnimeCategory.Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
