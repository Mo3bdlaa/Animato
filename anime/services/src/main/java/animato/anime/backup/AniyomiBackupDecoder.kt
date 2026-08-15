package animato.anime.backup

import android.content.Context
import android.net.Uri
import animato.anime.backup.models.AniyomiBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * Reads an Aniyomi backup file.
 *
 * Mihon has a decoder and this is not it. Two of Aniyomi's field numbers mean something else in a
 * Mihon backup, and one of them — 106, extension apks against extension stores — makes Mihon's
 * decoder throw the whole file away. Reading the file here is what lets a user with an ordinary
 * Aniyomi backup get their library back at all, and it costs one class that knows two layouts.
 *
 * Nothing about Mihon's own restore changes. A Mihon backup still goes through Mihon's screen.
 */
class AniyomiBackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {

    /**
     * Reads the backup at [uri], gzipped or not.
     *
     * @throws IOException when the file is not a backup this can read.
     */
    fun decode(uri: Uri): AniyomiBackup {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val source = input.source().buffer()

            val peeked = source.peek().apply { require(2) }
            when (peeked.readShort().toInt()) {
                GZIP_MAGIC -> source.gzip().buffer()
                JSON_MAGIC_EMPTY, JSON_MAGIC_KEY, JSON_MAGIC_NEWLINE -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }.use { it.readByteArray() }
        } ?: throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))

        return try {
            AniyomiBackupFormat.decode(bytes, parser)
        } catch (_: SerializationException) {
            throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
        }
    }

    private companion object {
        const val GZIP_MAGIC = 0x1f8b
        const val JSON_MAGIC_EMPTY = 0x7b7d // `{}`
        const val JSON_MAGIC_KEY = 0x7b22 // `{"`
        const val JSON_MAGIC_NEWLINE = 0x7b0a // `{\n`
    }
}
