package tachiyomi.data.handlers.anime

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import tachiyomi.mi.data.AnimeDatabase

/**
 * The anime database, behind the same interface it has always had.
 *
 * `:anime:data` generates async queries now, so a query is awaited rather than executed and the
 * database is a `SuspendingTransacter`. Every caller of this handler was already in a coroutine, so
 * the interface did not have to change and no repository did.
 *
 * ## What this used to have to do, and no longer does
 *
 * Aniyomi's version carried a copy of Room's `withTransaction`: a transaction element in the
 * coroutine context, a control job, a thread taken over from the query executor and held until the
 * transaction ended, and a `runBlocking` inside it to bridge back to suspending code. All of that
 * existed because a synchronous SQLDelight transaction is confined to the thread that opened it and
 * a coroutine is not.
 *
 * A `SuspendingTransacter` is confined by the driver instead — `AndroidxSqliteDriver` implements
 * `SuspendingTransacter.TransactionDispatcher` and routes the body onto its own writer connection.
 * So `AnimeTransactionContext.kt` is gone, and so is the query dispatcher: sending a read to IO by
 * hand would only be dispatching it twice.
 */
class AndroidAnimeDatabaseHandler(
    val db: AnimeDatabase,
    /**
     * For the flows below, which hop off whatever thread the driver notified them on. Reads and
     * writes need no dispatcher of their own — the driver has one.
     */
    private val flowDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnimeDatabaseHandler {

    override suspend fun <T> await(inTransaction: Boolean, block: suspend AnimeDatabase.() -> T): T {
        return dispatch(inTransaction, block)
    }

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend AnimeDatabase.() -> Query<T>,
    ): List<T> {
        return dispatch(inTransaction) { block(db).awaitAsList() }
    }

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend AnimeDatabase.() -> Query<T>,
    ): T {
        return dispatch(inTransaction) { block(db).awaitAsOne() }
    }

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend AnimeDatabase.() -> ExecutableQuery<T>,
    ): T {
        return dispatch(inTransaction) { block(db).awaitAsOne() }
    }

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend AnimeDatabase.() -> Query<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).awaitAsOneOrNull() }
    }

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend AnimeDatabase.() -> ExecutableQuery<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).awaitAsOneOrNull() }
    }

    override fun <T : Any> subscribeToList(block: AnimeDatabase.() -> Query<T>): Flow<List<T>> {
        return block(db).asFlow().mapToList(flowDispatcher)
    }

    override fun <T : Any> subscribeToOne(block: AnimeDatabase.() -> Query<T>): Flow<T> {
        return block(db).asFlow().mapToOne(flowDispatcher)
    }

    override fun <T : Any> subscribeToOneOrNull(block: AnimeDatabase.() -> Query<T>): Flow<T?> {
        return block(db).asFlow().mapToOneOrNull(flowDispatcher)
    }

    override fun <T : Any> subscribeToPagingSource(
        countQuery: AnimeDatabase.() -> Query<Long>,
        queryProvider: AnimeDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingAnimeSource(
            countQuery = { countQuery(db) },
            queryProvider = { limit, offset -> queryProvider(db, limit, offset) },
        )
    }

    private suspend fun <T> dispatch(inTransaction: Boolean, block: suspend AnimeDatabase.() -> T): T {
        if (!inTransaction) return block(db)
        return db.transactionWithResult { block(db) }
    }
}
