package animato.di

import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import mihon.app.di.AppGraph
import mihon.core.metro.GraphProvider
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * Mihon's objects, found by type, for code that still asks Injekt for them.
 *
 * ## Why this exists
 *
 * Mihon moved its dependency injection from Injekt to Metro, a compile-time graph. What it left in
 * Injekt is a read-only shim that answers for seven types — enough for extensions, which is what it
 * is for. Animato's anime half asks Injekt for its own types and for a hundred-odd of Mihon's: its
 * preferences, repositories, interactors and managers. Porting all of that to Metro is not
 * possible from here: a Metro graph is assembled when the module that declares it compiles, and
 * that module is Mihon's, which cannot see ours.
 *
 * So this answers Injekt's question — *an instance of this type* — out of Mihon's graph, in three
 * steps, first answer wins:
 *
 * 1. **The graph's own accessors.** `AppGraph` exposes forty-odd types by name, and those are
 *    exactly what Mihon itself considers public.
 * 2. **The graph's providers.** The generated graph holds a provider for every binding it built,
 *    scoped singletons included, typed by the class it builds. Each is indexed under that class and
 *    every interface and superclass of it, so a request for `MangaRepository` finds the provider of
 *    `MangaRepositoryImpl`. A type two providers could both answer is left out rather than
 *    guessed.
 * 3. **Construction.** An interactor Mihon never needed a provider for — `GetChapter`, say — is
 *    built from its constructor, every parameter resolved through Injekt again. Only for Mihon's
 *    own packages, and never for a type a provider exists for, so a singleton the graph holds is
 *    never built a second time: two of those would mean two database connections, two download
 *    queues. A class marked `@SingleIn` that the graph does *not* hold is built once and kept.
 *
 * The graph is read lazily. Mihon builds it in `App.onCreate` after a step that must come first
 * (WebView's data directory), so touching it earlier would be a crash in a secondary process.
 */
internal class MihonGraphBridge(
    private val graphProvider: GraphProvider<AppGraph>,
    private val resolve: (Type) -> Any?,
) {
    private val graph: AppGraph get() = graphProvider.graph

    private val sources: Map<Class<*>, () -> Any?> by lazy { index() }

    /** Types nothing in the graph builds, remembered so construction is not retried per call. */
    private val unbuildable = ConcurrentHashMap.newKeySet<Class<*>>()

    /** Scoped types built here because the graph held none — one each, for the life of the app. */
    private val scoped = ConcurrentHashMap<Class<*>, Any>()

    fun lookup(type: Type): Any? {
        val raw = type.rawClass() ?: return null
        sources[raw]?.let { source -> return source() }
        return construct(raw)
    }

    private fun index(): Map<Class<*>, () -> Any?> {
        val found = HashMap<Class<*>, () -> Any?>()

        AppGraph::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType != Void.TYPE && it.name.startsWith("get") }
            .forEach { method -> found.putIfAbsent(method.returnType) { method.invoke(graph) } }

        val fromProviders = HashMap<Class<*>, () -> Any?>()
        val ambiguous = HashSet<Class<*>>()
        generateSequence(graph.javaClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { Provider::class.java.isAssignableFrom(it.type) && !Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                val built = (field.genericType as? ParameterizedType)
                    ?.actualTypeArguments?.firstOrNull()?.rawClass() ?: return@forEach
                field.isAccessible = true
                val source = { (field.get(graph) as? Provider<*>)?.invoke() }
                for (type in built.withSupertypes()) {
                    if (type in found) continue
                    if (fromProviders.put(type, source) != null) ambiguous += type
                }
            }
        ambiguous.forEach(fromProviders::remove)

        return found + fromProviders
    }

    private fun construct(type: Class<*>): Any? {
        if (type in unbuildable || !type.isMihons() || type.isInterface || Modifier.isAbstract(type.modifiers)) {
            return null
        }
        if (type.isAnnotationPresent(SingleIn::class.java)) {
            scoped[type]?.let { return it }
            synchronized(scoped) {
                scoped[type]?.let { return it }
                return build(type)?.also { scoped[type] = it }
            }
        }
        return build(type)
    }

    private fun build(type: Class<*>): Any? {
        val constructor = type.constructors
            .filterNot { it.isSynthetic }
            .maxByOrNull { it.parameterCount }
        if (constructor == null) {
            unbuildable += type
            return null
        }
        val arguments = constructor.genericParameterTypes.map { parameter ->
            resolve(parameter) ?: run {
                unbuildable += type
                return null
            }
        }
        return constructor.newInstance(*arguments.toTypedArray())
    }

    private fun Class<*>.isMihons(): Boolean = MIHON_PACKAGES.any { name.startsWith(it) }

    private fun Class<*>.withSupertypes(): Set<Class<*>> {
        val all = LinkedHashSet<Class<*>>()
        fun visit(type: Class<*>?) {
            if (type == null || type == Any::class.java || !all.add(type)) return
            visit(type.superclass)
            type.interfaces.forEach(::visit)
        }
        visit(this)
        return all
    }

    private companion object {
        val MIHON_PACKAGES = listOf("eu.kanade.", "tachiyomi.", "mihon.")
    }
}

private fun Type.rawClass(): Class<*>? = when (this) {
    is Class<*> -> this
    is ParameterizedType -> rawType as? Class<*>
    else -> null
}
