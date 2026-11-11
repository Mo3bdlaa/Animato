package tachiyomi.domain.category.anime.interactor

import animato.domain.category.AnimeCategoryUpdate
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tachiyomi.domain.category.anime.repository.AnimeCategoryRepository

@Inject
class UpdateAnimeCategory(
    private val categoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(payload: AnimeCategoryUpdate): Result = withContext(NonCancellable) {
        try {
            categoryRepository.updatePartialAnimeCategory(payload)
            Result.Success
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class Error(val error: Exception) : Result
    }
}
