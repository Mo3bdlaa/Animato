package animato.anime.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * An extension store as an Aniyomi backup stores it.
 *
 * [signingKey] is at 5 and [contactWebsite] at 4 — out of order against the declaration, which is
 * how Aniyomi wrote them. The numbers are what matter and the declaration order is cosmetic, so
 * the fields stay in the order that reads well.
 *
 * The three nullable fields arrived after the format did. A store from an older backup has no
 * badge label, no Discord contact and no legacy flag, and the restorer fills those in.
 *
 * Every field has a default, including the two the writer always writes. A store is a convenience
 * — it saves the user finding a URL again — and no part of it is worth failing a whole restore
 * over, so a malformed one arrives blank and is dropped rather than throwing.
 */
@Serializable
data class BackupAnimeExtensionStore(
    @ProtoNumber(1) val indexUrl: String = "",
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val badgeLabel: String? = null,
    @ProtoNumber(5) val signingKey: String = "",
    @ProtoNumber(4) val contactWebsite: String = "",
    @ProtoNumber(6) val contactDiscord: String? = null,
    @ProtoNumber(7) val isLegacy: Boolean? = null,
    @ProtoNumber(8) val extensionListUrl: String? = null,
)
