package animato.anime.backup.models

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A manga extension repository as an Aniyomi backup stores it.
 *
 * This is Mihon's own `BackupExtensionStore` at an earlier point in its life, when a repository
 * was five fields and was called a repository. The five it has still line up one-for-one with the
 * five Mihon kept, only renamed, so [toBackupExtensionStore] is a rename and nothing more.
 *
 * Mihon's model cannot be used to read these directly: its later fields have no defaults, so a
 * five-field message decoded into it fails on the three that are missing.
 */
@Serializable
data class BackupMangaExtensionStore(
    @ProtoNumber(1) val baseUrl: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "",
    @ProtoNumber(5) val signingKeyFingerprint: String = "",
) {

    /**
     * The same repository in the shape Mihon's restorer takes.
     *
     * `isLegacy` is true because that is what this repository is: one that serves the old index
     * format. Every repository written by a backup of this age serves that format.
     */
    fun toBackupExtensionStore() = BackupExtensionStore(
        indexUrl = baseUrl,
        name = name,
        badgeLabel = shortName,
        signingKey = signingKeyFingerprint,
        contactWebsite = website,
        contactDiscord = null,
        isLegacy = true,
        extensionListUrl = null,
    )
}
