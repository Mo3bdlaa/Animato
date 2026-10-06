package animato.di

import dev.zacsweers.metro.Provider
import mihon.app.di.AppGraph
import tachiyomi.data.Database
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * Mihon's manga database — the one object Animato needs from Mihon's graph that the graph does not
 * expose, and cannot be given a second copy of.
 *
 * Every manga repository [MihonBindings] builds wraps this, and they have to wrap *this one*:
 * SQLDelight notifies a query's listeners through the driver it was run on, so a second database
 * on a second driver would write the same file and leave every Mihon screen watching it unaware.
 *
 * `AppGraph` does not expose it and Mihon's files are not ours to change, so it is read from the
 * generated graph: the provider field whose generic type is `Provider<Database>`. This is the only
 * reflection left in Animato's dependency injection, and it is narrow on purpose — one type,
 * matched exactly, failing loudly at start-up rather than on whichever screen first needs a manga.
 * R8 keeps the field and its generic signature (animato-app/proguard-rules.pro), and
 * check-dex-keeps.sh verifies the release APK still carries both.
 */
internal object MihonDatabase {

    fun from(graph: AppGraph): Database {
        val field = generateSequence(graph.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    Provider::class.java.isAssignableFrom(field.type) &&
                    (field.genericType as? ParameterizedType)?.actualTypeArguments?.singleOrNull() ==
                    Database::class.java
            }
            ?: error(
                "Mihon's graph (${graph.javaClass.name}) has no Provider<Database> field. Either Mihon " +
                    "changed how it provides the database, or R8 stripped the field's generic signature.",
            )
        field.isAccessible = true
        return (field.get(graph) as Provider<*>).invoke() as Database
    }
}
