package animato.anime.di

/**
 * The scope of Animato's own Metro graph, `animato.di.AnimatoGraph`.
 *
 * Not Mihon's `AppScope`, on purpose. A graph aggregates every contribution to its scope that is on
 * its classpath, and Animato's graph can see all of Mihon's modules: in `AppScope` it would gather
 * Mihon's bindings too and build a second copy of everything Mihon already holds — a second
 * database connection, a second download queue. In a scope of its own it sees only what this side
 * contributes, and what it takes from Mihon it takes from Mihon's graph, by name.
 *
 * Lives here because every anime module but the extension API depends on `:anime:domain`.
 */
abstract class AnimatoScope private constructor()
