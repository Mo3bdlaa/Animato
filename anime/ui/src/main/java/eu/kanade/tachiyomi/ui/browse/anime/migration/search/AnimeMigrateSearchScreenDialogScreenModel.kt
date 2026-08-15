package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeMigrateSearchScreenDialogScreenModel(
    val animeId: Long,
    getAnime: GetAnime = Injekt.get(),
) : ViewModel() {

    val state: StateFlow<AnimeMigrateSearchScreenDialogScreenModel.State>
        field = MutableStateFlow<AnimeMigrateSearchScreenDialogScreenModel.State>(State())

    init {
        viewModelScope.launch {
            val anime = getAnime.await(animeId)!!

            state.update {
                it.copy(anime = anime)
            }
        }
    }

    fun setDialog(dialog: Dialog?) {
        state.update {
            it.copy(dialog = dialog)
        }
    }

    @Immutable
    data class State(
        val anime: Anime? = null,
        val dialog: Dialog? = null,
    )

    sealed interface Dialog {
        data class Migrate(val anime: Anime) : Dialog
    }
}
