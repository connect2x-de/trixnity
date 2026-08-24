package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.store.RoomAccountDataStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomAccountDataEvent
import de.connect2x.trixnity.core.model.events.RoomAccountDataEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope

class RoomAccountDataEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomAccountDataStore: RoomAccountDataStore,
    private val tm: StoreTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync
            .subscribeEventList(Priority.STORE_EVENTS, subscriber = ::setRoomAccountData)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun setRoomAccountData(accountData: List<RoomAccountDataEvent<RoomAccountDataEventContent>>) {
        if (accountData.isNotEmpty()) tm.writeTransaction { accountData.forEach { roomAccountDataStore.save(it) } }
    }
}
