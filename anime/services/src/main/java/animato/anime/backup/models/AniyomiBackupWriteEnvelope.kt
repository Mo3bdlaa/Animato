package animato.anime.backup.models

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * What a backup is written from, as opposed to what one is read into.
 *
 * Reading and writing want opposite things from a protobuf model, and there is no way to ask for
 * both from one declaration.
 *
 * **Reading wants a default on every field.** [AniyomiBackupEnvelope] has one, so a backup written
 * by an older app that did not have a field yet still opens instead of failing on it.
 *
 * **Writing wants a default on none of them.** Protobuf leaves out a field holding its default, and
 * the reader is supposed to put it back — but Aniyomi's and Mihon's models declare no defaults for
 * their extension stores and custom buttons, so a field left out is a field missing, and a missing
 * field fails the entire file. Turning defaults on globally is not the answer either: a nullable
 * field with a default cannot be encoded at all, because protobuf has no way to write a null.
 *
 * So the three models that differ are declared strictly here, and everything else is shared. The
 * two extension stores are not new types at all — they are Mihon's own model, which happens to be
 * exactly the shape Aniyomi uses for both of its stores.
 *
 * `AnimatoBackupInteropTest` is what keeps this honest: it decodes what this writes with Mihon's
 * real model and with Aniyomi's field rules, and fails if either would have rejected the file.
 */
@Serializable
internal data class AniyomiBackupWriteEnvelope(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) val backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) val backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) val backupMangaExtensionStores: List<BackupExtensionStore> = emptyList(),

    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(503) val backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(505) val backupAnimeExtensionStores: List<BackupExtensionStore> = emptyList(),
    @ProtoNumber(506) val backupCustomButtons: List<WritableBackupCustomButton> = emptyList(),
)

/**
 * A custom button with no defaults, so that every field of it reaches the file.
 *
 * Aniyomi's own model declares none either. A button written the ordinary way, with its empty
 * strings left out, is one Aniyomi cannot read — and it would take the rest of the backup with it.
 */
@Serializable
internal data class WritableBackupCustomButton(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val isFavorite: Boolean,
    @ProtoNumber(3) val sortIndex: Long,
    @ProtoNumber(4) val content: String,
    @ProtoNumber(5) val longPressContent: String,
    @ProtoNumber(6) val onStartup: String,
)

internal fun BackupCustomButton.toWritable() = WritableBackupCustomButton(
    name = name,
    isFavorite = isFavorite,
    sortIndex = sortIndex,
    content = content,
    longPressContent = longPressContent,
    onStartup = onStartup,
)

/**
 * The anime store in the shape both other apps read it in.
 *
 * The absent cases are filled rather than left out, because a reader that treats these as mandatory
 * would otherwise fail on them: a store with no badge is labelled by its name, and one written
 * before the new index format existed is a legacy store.
 */
internal fun BackupAnimeExtensionStore.toWritable() = BackupExtensionStore(
    indexUrl = indexUrl,
    name = name,
    badgeLabel = badgeLabel ?: name,
    signingKey = signingKey,
    contactWebsite = contactWebsite,
    contactDiscord = contactDiscord,
    isLegacy = isLegacy ?: true,
    extensionListUrl = extensionListUrl,
)
