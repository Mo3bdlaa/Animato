package animato.anime.backup.restore

import animato.anime.backup.models.BackupCustomButton
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Brings back the player's custom buttons.
 *
 * These are scripts the user wrote and cannot get back from anywhere else, so they are worth the
 * restore even though nothing else depends on them. A button whose name is already taken is left
 * alone: the one in the library is the one the user has been pressing.
 *
 * At most one button is the favourite — the one bound to the player's quick action — so a backup's
 * favourite is only honoured when the library has not already chosen one.
 */
class CustomButtonRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCustomButtons: GetCustomButtons = Injekt.get(),
) {

    suspend operator fun invoke(backupCustomButtons: List<BackupCustomButton>) {
        if (backupCustomButtons.isEmpty()) return

        val dbCustomButtons = getCustomButtons.getAll()
        val existingNames = dbCustomButtons.map { it.name }.toSet()
        var nextSortIndex = dbCustomButtons.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0
        var favouriteTaken = dbCustomButtons.any { it.isFavorite }

        backupCustomButtons
            .sortedBy { it.sortIndex }
            .filter { it.name !in existingNames }
            .forEach { button ->
                val isFavorite = button.isFavorite && !favouriteTaken
                favouriteTaken = favouriteTaken || isFavorite
                handler.await(inTransaction = true) {
                    custom_buttonsQueries.insert(
                        name = button.name,
                        isFavorite = isFavorite,
                        sortIndex = nextSortIndex++,
                        content = button.content,
                        longPressContent = button.longPressContent,
                        onStartup = button.onStartup,
                    )
                }
            }
    }
}
