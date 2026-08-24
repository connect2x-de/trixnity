package de.connect2x.trixnity.client.user

import de.connect2x.trixnity.client.store.GlobalAccountDataStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope

class GlobalAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val globalAccountDataStore: GlobalAccountDataStore,
    private val tm: StoreTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.STORE_EVENTS, subscriber = ::setGlobalAccountData).unsubscribeOnCompletion(scope)
    }

    internal suspend fun setGlobalAccountData(syncEvents: SyncEvents) {
        val events = syncEvents.syncResponse.accountData?.events
        if (events?.isNotEmpty() == true) tm.writeTransaction { events.forEach { globalAccountDataStore.save(it) } }
    }
}
