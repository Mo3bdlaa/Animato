package animato.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.presentation.more.AppMigratingScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import java.io.RandomAccessFile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Mihon's migrating screen in place of [content] while either flag is up, held for at least a
 * second once shown so a quick migration doesn't just flash it. [onShown] releases the splash so
 * the reason for the wait is visible.
 */
@Composable
fun MigrationGate(
    appMigrating: StateFlow<Boolean>,
    databaseMigrating: StateFlow<Boolean>,
    onShown: () -> Unit,
    content: @Composable () -> Unit,
) {
    val app by appMigrating.collectAsState()
    val database by databaseMigrating.collectAsState()
    val migrating = app || database
    var migratingSince by remember { mutableStateOf(if (migrating) TimeSource.Monotonic.markNow() else null) }
    LaunchedEffect(migrating) {
        val since = migratingSince
        if (migrating) {
            if (since == null) migratingSince = TimeSource.Monotonic.markNow()
        } else if (since != null) {
            delay(1.seconds - since.elapsedNow())
            migratingSince = null
        }
    }
    if (migrating || migratingSince != null) {
        AppMigratingScreen()
        LaunchedEffect(Unit) { onShown() }
    } else {
        content()
    }
}

/**
 * Whether Mihon's manga database is on an older schema than this build, so opening it will migrate.
 *
 * Mihon tracks this in its `DatabaseRepository`, which its graph doesn't expose. The version is read
 * from the file header rather than through SQLite: a second SQLite copy opening the file beside
 * Mihon's bundled one can corrupt it. A version still sitting in the WAL reads as older, which at
 * worst shows the migrating screen until the first query returns.
 */
object MihonDatabaseVersion {

    private const val USER_VERSION_OFFSET = 60L

    @Volatile
    private var checked: Boolean? = null

    fun needsUpgrade(context: Context): Boolean {
        checked?.let { return it }
        return read(context).also { checked = it }
    }

    /** The schema is current from here on, whatever the header said at launch. */
    fun markUpgraded() {
        checked = false
    }

    private fun read(context: Context): Boolean {
        val file = context.getDatabasePath("tachiyomi.db")
        if (!file.exists()) return false
        return try {
            val version = RandomAccessFile(file, "r").use {
                it.seek(USER_VERSION_OFFSET)
                it.readInt()
            }
            version in 1..<Database.Schema.version
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't read the manga database version" }
            false
        }
    }
}
