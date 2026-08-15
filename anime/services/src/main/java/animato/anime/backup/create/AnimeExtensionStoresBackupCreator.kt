package animato.anime.backup.create

import animato.anime.backup.models.BackupAnimeExtensionStore
import mihon.domain.extension.anime.interactor.GetAnimeExtensionStores
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Writes the anime extension stores.
 *
 * The apks are not written, on purpose — see the divergence log. The store is the part that makes
 * getting them back easy while leaving the decision with the user.
 */
class AnimeExtensionStoresBackupCreator(
    private val getExtensionStores: GetAnimeExtensionStores = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupAnimeExtensionStore> {
        return getExtensionStores.get().map { store ->
            BackupAnimeExtensionStore(
                indexUrl = store.indexUrl,
                name = store.name,
                badgeLabel = store.badgeLabel,
                signingKey = store.signingKey,
                contactWebsite = store.contact.website,
                contactDiscord = store.contact.discord,
                isLegacy = store.isLegacy,
                extensionListUrl = store.extensionListUrl,
            )
        }
    }
}
