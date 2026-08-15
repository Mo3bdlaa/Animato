package animato.anime.backup.create

import animato.anime.backup.models.BackupCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Writes the player's custom buttons.
 *
 * Small, and the only thing in the app the user wrote themselves rather than chose from a list.
 */
class CustomButtonBackupCreator(
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCustomButton> {
        return getCustomButtons.getAll().map {
            BackupCustomButton(
                name = it.name,
                isFavorite = it.isFavorite,
                sortIndex = it.sortIndex,
                content = it.content,
                longPressContent = it.longPressContent,
                onStartup = it.onStartup,
            )
        }
    }
}
