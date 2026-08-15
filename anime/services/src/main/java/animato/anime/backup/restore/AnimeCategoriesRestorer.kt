package animato.anime.backup.restore

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Recreates the backup's anime categories, keeping the ones already here.
 *
 * Categories are matched by name, which is also how an anime finds its way back into one. A name
 * the library already has is left exactly as it is — the backup does not get to reorder or
 * reconfigure a category the user has since changed.
 */
class AnimeCategoriesRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getAnimeCategories.await()
        val existingByName = dbCategories.associateBy { it.name }
        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

        val restored = backupCategories
            .sortedBy { it.order }
            .filter { it.name !in existingByName }
            .map { backupCategory ->
                val order = nextOrder++
                handler.await(inTransaction = true) {
                    categoriesQueries.insert(backupCategory.name, order, backupCategory.flags)
                }
                backupCategory.flags
            }

        // Whether per-category display settings are on is one switch for the whole app, and the
        // manga half of the same restore also has an opinion about it. Turning it on when this
        // backup needs it, and never off, is what lets both halves have their say.
        val needsPerCategorySettings = (dbCategories.map { it.flags } + restored).distinct().size > 1
        if (needsPerCategorySettings) {
            libraryPreferences.categorizedDisplaySettings.set(true)
        }
    }
}
