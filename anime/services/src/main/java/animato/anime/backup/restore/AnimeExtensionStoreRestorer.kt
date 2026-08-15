package animato.anime.backup.restore

import animato.anime.backup.models.BackupAnimeExtensionStore
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Adds back the extension stores the backup was using.
 *
 * This is the part of a restore that makes the rest of it work. A library full of entries from a
 * source that is not installed is a list of titles and nothing else, and the store is what the
 * user needs in order to install the extension again.
 *
 * The three fields that a backup of any age may be missing are filled in the way the store screen
 * would have: the name doubles as the badge, and a store from before the new index format existed
 * is a legacy one.
 */
class AnimeExtensionStoreRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
) {

    suspend operator fun invoke(store: BackupAnimeExtensionStore) {
        // The index URL is the store. Without one there is nothing to add and nothing to say.
        if (store.indexUrl.isBlank()) return

        handler.await(inTransaction = true) {
            extension_storeQueries.upsert(
                indexUrl = store.indexUrl,
                name = store.name,
                badgeLabel = store.badgeLabel ?: store.name,
                signingKey = store.signingKey,
                contactWebsite = store.contactWebsite,
                contactDiscord = store.contactDiscord,
                isLegacy = store.isLegacy ?: true,
                extensionListUrl = store.extensionListUrl,
            )
        }
    }
}
