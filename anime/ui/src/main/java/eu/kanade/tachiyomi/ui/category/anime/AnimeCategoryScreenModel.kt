package eu.kanade.tachiyomi.ui.category.anime

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animato.domain.category.AnimeCategory
import aniyomi.domain.library.service.AnimeLibraryPreferences
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.anime.interactor.CreateAnimeCategoryWithName
import tachiyomi.domain.category.anime.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.category.anime.interactor.HideAnimeCategory
import tachiyomi.domain.category.anime.interactor.RenameAnimeCategory
import tachiyomi.domain.category.anime.interactor.ReorderAnimeCategory
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoryScreenModel(
    private val getAllCategories: GetAnimeCategories = Injekt.get(),
    private val getVisibleCategories: GetVisibleAnimeCategories = Injekt.get(),
    private val createCategoryWithName: CreateAnimeCategoryWithName = Injekt.get(),
    private val hideCategory: HideAnimeCategory = Injekt.get(),
    private val deleteCategory: DeleteAnimeCategory = Injekt.get(),
    private val reorderCategory: ReorderAnimeCategory = Injekt.get(),
    private val renameCategory: RenameAnimeCategory = Injekt.get(),
    private val libraryPreferences: AnimeLibraryPreferences = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<AnimeCategoryScreenState>
        field = MutableStateFlow<AnimeCategoryScreenState>(AnimeCategoryScreenState.Loading)

    private val _events: Channel<AnimeCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val allCategories = if (libraryPreferences.hideHiddenCategoriesSettings().get()) {
                getVisibleCategories.subscribe()
            } else {
                getAllCategories.subscribe()
            }

            allCategories.collectLatest { categories ->
                state.update {
                    AnimeCategoryScreenState.Success(
                        categories = categories
                            .filterNot(AnimeCategory::isSystemCategory)
                            .toImmutableList(),
                    )
                }
            }
        }
    }

    fun createCategory(name: String) {
        viewModelScope.launch {
            when (createCategoryWithName.await(name)) {
                is CreateAnimeCategoryWithName.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )

                else -> {}
            }
        }
    }

    fun hideCategory(category: AnimeCategory) {
        viewModelScope.launch {
            when (hideCategory.await(category)) {
                is HideAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            when (deleteCategory.await(categoryId = categoryId)) {
                is DeleteAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(category: AnimeCategory, newIndex: Int) {
        viewModelScope.launch {
            when (reorderCategory.await(category, newIndex)) {
                is ReorderAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun renameCategory(category: AnimeCategory, name: String) {
        viewModelScope.launch {
            when (renameCategory.await(category, name)) {
                is RenameAnimeCategory.Result.InternalError -> _events.send(
                    AnimeCategoryEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: AnimeCategoryDialog) {
        state.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        state.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface AnimeCategoryDialog {
    data object Create : AnimeCategoryDialog
    data class Rename(val category: AnimeCategory) : AnimeCategoryDialog
    data class Delete(val category: AnimeCategory) : AnimeCategoryDialog
}

sealed interface AnimeCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCategoryScreenState {

    @Immutable
    data object Loading : AnimeCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<AnimeCategory>,
        val dialog: AnimeCategoryDialog? = null,
    ) : AnimeCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
