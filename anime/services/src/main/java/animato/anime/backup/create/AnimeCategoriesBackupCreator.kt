package animato.anime.backup.create

import animato.domain.category.AnimeCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Writes the anime categories, minus the one nobody made.
 *
 * The default category exists in every library and restoring it would add a second copy, so it is
 * left out — the same thing Mihon's own creator does with its own.
 */
class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getAnimeCategories.await()
            .filterNot(AnimeCategory::isSystemCategory)
            .map {
                BackupCategory(
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                )
            }
    }
}
