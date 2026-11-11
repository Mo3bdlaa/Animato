package animato.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
 * Mihon tracks this in its `DatabaseRepository`, which its graph doesn't expose; the version is
 * read straight from the file instead, before Mihon's driver opens it.
 */
object MihonDatabaseVersion {

    fun needsUpgrade(context: Context): Boolean {
        val file = context.getDatabasePath("tachiyomi.db")
        if (!file.exists()) return false
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
                .let { it in 1..<Database.Schema.version }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Couldn't read the manga database version" }
            false
        }
    }
}
