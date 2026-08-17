package de.connect2x.trixnity.client.store.cache

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.utils.ConcurrentList
import de.connect2x.trixnity.utils.concurrentMutableList

private val log = Logger("de.connect2x.trixnity.client.store.cache.CacheTransaction")

interface CacheTransaction {
    val onCommitActions: ConcurrentList<suspend () -> Unit>
    val onRollbackActions: ConcurrentList<suspend () -> Unit>
}

class CacheTransactionImpl : CacheTransaction {
    override val onCommitActions = concurrentMutableList<suspend () -> Unit>()
    override val onRollbackActions = concurrentMutableList<suspend () -> Unit>()
}

suspend fun <T> withCacheTransaction(block: suspend CacheTransaction.() -> T): T {
    val cacheTransaction = CacheTransactionImpl()
    try {
        val result = block(cacheTransaction)
        val onCommitActions = cacheTransaction.onCommitActions.read { toList() }
        if (onCommitActions.isNotEmpty()) {
            log.trace { "apply commit actions for transaction" }
            onCommitActions.forEach { it() }
        }
        return result
    } catch (exception: Exception) {
        val onRollbackActions = cacheTransaction.onRollbackActions.read { reversed() }
        if (onRollbackActions.isNotEmpty()) {
            log.debug { "apply rollback actions for transaction due to $exception" }
            onRollbackActions.forEach { it() }
        }
        throw exception
    }
}
