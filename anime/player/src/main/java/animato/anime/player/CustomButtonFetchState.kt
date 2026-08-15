package animato.anime.player

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.custombuttons.model.CustomButton

/**
 * Whether the player's custom buttons have loaded yet.
 *
 * Aniyomi declared it inside `PlayerSettingsCustomButtonScreenModel.kt`, so the player's view model
 * imported a settings screen to describe its own loading state. The settings screen is not part of
 * this stage; the state is, because the player shows the buttons.
 */
sealed interface CustomButtonFetchState {
    @Immutable
    data object Loading : CustomButtonFetchState

    @Immutable
    data class Success(val customButtons: ImmutableList<CustomButton>) : CustomButtonFetchState

    @Immutable
    data class Error(val errorMessage: String) : CustomButtonFetchState
}

/**
 * The buttons if they loaded, an empty list otherwise.
 *
 * Aniyomi kept this beside the settings screen model; it is a property of the state, and the player
 * is the thing that reads it.
 */
fun CustomButtonFetchState.getButtons(): ImmutableList<CustomButton> = when (this) {
    is CustomButtonFetchState.Success -> customButtons
    else -> persistentListOf()
}
