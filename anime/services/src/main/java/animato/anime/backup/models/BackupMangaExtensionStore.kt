package animato.anime.backup.models

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A manga extension store as it sits at field 106, in either app's backup.
 *
 * Mihon and Aniyomi wrote the same five fields there under different names — a repository grew
 * into a store — and Mihon has since added three more. Reading all eight with a default for every
 * one covers both: an Aniyomi backup fills the first five, a Mihon backup fills all eight, and
 * neither fails on what the other left out.
 *
 * Mihon's own model cannot be used to read these. Its later fields have no defaults, so the
 * five-field message an Aniyomi backup carries dies on the three that are absent.
 */
@Serializable
data class BackupMangaExtensionStore(
    @ProtoNumber(1) val baseUrl: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val shortName: String? = null,
    @ProtoNumber(4) val website: String = "",
    @ProtoNumber(5) val signingKeyFingerprint: String = "",
    @ProtoNumber(6) val contactDiscord: String? = null,
    @ProtoNumber(7) val isLegacy: Boolean? = null,
    @ProtoNumber(8) val extensionListUrl: String? = null,
) {

    /**
     * The same store in the shape Mihon's restorer takes.
     *
     * A backup with no `isLegacy` was written before the new index format existed, so what it
     * describes is a legacy store — which is why the absent case is true rather than false.
     */
    fun toBackupExtensionStore() = BackupExtensionStore(
        indexUrl = baseUrl,
        name = name,
        badgeLabel = shortName,
        signingKey = signingKeyFingerprint,
        contactWebsite = website,
        contactDiscord = contactDiscord,
        isLegacy = isLegacy ?: true,
        extensionListUrl = extensionListUrl,
    )
}
