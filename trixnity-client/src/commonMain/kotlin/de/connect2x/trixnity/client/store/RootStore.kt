package de.connect2x.trixnity.client.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

class RootStore(private val stores: List<Store>) : Store {
    private val hasBeenInit = MutableStateFlow(false)

    override suspend fun init(coroutineScope: CoroutineScope) {
        if (hasBeenInit.getAndUpdate { true }.not())
            stores.forEach { it.init(coroutineScope) }
    }

    context(transaction: StoreWriteTransaction)
    override suspend fun clearCache() {
        stores.forEach { it.clearCache() }
    }

    context(transaction: StoreWriteTransaction)
    override suspend fun deleteAll() {
        stores.forEach { it.deleteAll() }
    }
}
