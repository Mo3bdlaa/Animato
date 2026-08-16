package animato.app.sync

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

/**
 * Where the shared folder is, and what this device has already seen in it.
 *
 * ## Why there is no server here
 *
 * Sync is the most-asked-for unbuilt feature and the usual answers all cost something this project
 * would rather not spend: a server somebody has to run and keep running, a Drive integration that
 * drags in Play Services and an OAuth client, or a hosted service that makes a reading history
 * somebody else's problem to store.
 *
 * The cheap answer is that **the file sync already exists on the user's device**. Nextcloud,
 * Syncthing, Dropbox, Drive and a plain SMB share all present a folder, and Android hands any of
 * them to an app through the storage picker. So Animato writes a backup into that folder and reads
 * the ones other devices wrote — and every hard part of moving bytes between two phones is somebody
 * else's, already installed, already trusted with the user's files.
 *
 * ## Why the device id matters more than it looks
 *
 * Without it a device restores its own backup on the next run, which is not merely wasteful: a
 * restore is a merge, and merging a snapshot of yourself from an hour ago over yourself is how a
 * deletion comes back. The id goes in the filename because the folder listing is all a device gets
 * to look at before deciding whether to download something.
 */
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val enabled = preferenceStore.getBoolean("animato_sync_enabled", false)

    /** The shared folder, as a SAF tree URI. Empty until somebody picks one. */
    val folderUri = preferenceStore.getString("animato_sync_folder", "")

    /**
     * This installation's name in the folder.
     *
     * App state rather than a setting: it is not a preference anybody expressed, and it must not
     * travel in a backup — two devices restored from the same file would otherwise share an id and
     * stop seeing each other entirely.
     */
    val deviceId: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("animato_sync_device_id"),
        "",
    )

    /**
     * The modification time of the newest foreign backup already merged.
     *
     * A timestamp rather than a filename so that a device which has been off for a week merges only
     * the newest file rather than replaying six of them in order — the merge is idempotent but
     * running it six times is six restores.
     */
    val lastMergedAt = preferenceStore.getLong(
        Preference.appStateKey("animato_sync_last_merged"),
        0L,
    )

    val lastSyncedAt = preferenceStore.getLong(
        Preference.appStateKey("animato_sync_last_synced"),
        0L,
    )

    /** Hours between runs. */
    val intervalHours = preferenceStore.getInt("animato_sync_interval", DEFAULT_INTERVAL_HOURS)

    /** Assigns an id the first time one is needed, and never again. */
    fun deviceIdOrCreate(): String {
        deviceId.get().takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().take(ID_LENGTH).also { deviceId.set(it) }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 12

        val INTERVAL_CHOICES = listOf(1, 6, 12, 24)

        /**
         * Long enough not to collide, short enough to read in a file listing.
         *
         * Eight hex characters is about four billion, against a population of a person's own
         * devices. Somebody looking at the folder should be able to tell two of their phones apart.
         */
        private const val ID_LENGTH = 8
    }
}
