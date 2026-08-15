package animato.anime.backup

import animato.anime.backup.models.AniyomiBackup
import animato.anime.backup.models.AniyomiBackupEnvelope
import animato.anime.backup.models.BackupLayoutProbe
import animato.anime.backup.models.LegacyAniyomiBackupEnvelope
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Turns the bytes of an Aniyomi backup into models, whichever layout it was written in.
 *
 * Separate from [AniyomiBackupDecoder] because this is the part worth testing and the part that
 * has no business knowing about content resolvers or gzip.
 */
object AniyomiBackupFormat {

    /**
     * Reads an unpacked backup.
     *
     * The file is walked twice: once to see which layout it is, once to read it. Protobuf gives no
     * way to know without looking, and the alternative — reading it as one layout and falling back
     * to the other on a wrong answer — is the same two passes with a worse failure mode.
     */
    fun decode(bytes: ByteArray, parser: ProtoBuf = ProtoBuf): AniyomiBackup {
        val probe = parser.decodeFromByteArray(BackupLayoutProbe.serializer(), bytes)
        return if (probe.isLegacyLayout) {
            parser.decodeFromByteArray(LegacyAniyomiBackupEnvelope.serializer(), bytes).toAniyomiBackup()
        } else {
            parser.decodeFromByteArray(AniyomiBackupEnvelope.serializer(), bytes).toAniyomiBackup()
        }
    }
}
