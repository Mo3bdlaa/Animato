package tachiyomi.domain.custombuttons.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tachiyomi.domain.custombuttons.model.CustomButtonUpdate
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository

@Inject
class UpdateCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(update: CustomButtonUpdate) = withContext(NonCancellable) {
        try {
            customButtonRepository.updatePartialCustomButton(update)
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    suspend fun await(updates: List<CustomButtonUpdate>) = withContext(NonCancellable) {
        try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
