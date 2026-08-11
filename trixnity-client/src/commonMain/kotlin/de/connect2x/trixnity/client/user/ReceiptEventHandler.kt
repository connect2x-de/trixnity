package de.connect2x.trixnity.client.user

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.RoomUserStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.m.ReceiptEventContent
import de.connect2x.trixnity.core.model.events.m.ReceiptType
import de.connect2x.trixnity.core.model.events.roomIdOrNull
import de.connect2x.trixnity.core.subscribeContentList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlinx.coroutines.CoroutineScope

private val log = Logger("de.connect2x.trixnity.client.user.ReceiptEventHandler")

class ReceiptEventHandler(
    private val api: MatrixClientServerApiClient,
    private val roomUserStore: RoomUserStore,
    private val tm: StoreTransactionManager,
) : EventHandler {

    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribeContentList(Priority.STORE_EVENTS, subscriber = ::setReadReceipts)
            .unsubscribeOnCompletion(scope)
        api.sync.subscribe(Priority.STORE_EVENTS, subscriber = ::deleteReadReceiptsOnNonJoin)
            .unsubscribeOnCompletion(scope)
    }

    internal suspend fun setReadReceipts(receiptEvents: List<ClientEvent<ReceiptEventContent>>) {
        val receipts = receiptEvents.flatMap { receiptEvent ->
            receiptEvent.roomIdOrNull?.let { roomId ->
                log.trace { "set read receipts of room $roomId" }
                data class UserReceipt(
                    val userId: UserId,
                    val type: ReceiptType,
                    val receipt: RoomUserReceipts.Receipt,
                )

                val flattenReceipts = receiptEvent.content.events.flatMap { (eventId, receiptsByType) ->
                    receiptsByType.flatMap { (type, receiptsByUser) ->
                        receiptsByUser.map { (user, receipt) ->
                            UserReceipt(user, type, RoomUserReceipts.Receipt(eventId, receipt))
                        }
                    }
                }
                flattenReceipts.groupBy { it.userId }
                    .map { (userId, userReceipts) ->
                        val receipts = userReceipts.groupBy { it.type }.mapValues { it.value.last().receipt }
                        RoomUserReceipts(roomId, userId, receipts)
                    }
            }.orEmpty()
        }
        if (receipts.isNotEmpty()) {
            tm.writeTransaction {
                receipts.forEach { roomUserReceipts ->
                    roomUserStore.updateReceipts(
                        roomUserReceipts.userId,
                        roomUserReceipts.roomId
                    ) { oldRoomUserReceipts ->
                        oldRoomUserReceipts?.copy(receipts = oldRoomUserReceipts.receipts + roomUserReceipts.receipts)
                            ?: roomUserReceipts
                    }
                }
            }
        }
    }

    internal suspend fun deleteReadReceiptsOnNonJoin(syncEvents: SyncEvents) {
        val deleteReceiptsByRoomId =
            syncEvents.syncResponse.room?.invite?.keys.orEmpty() +
                    syncEvents.syncResponse.room?.knock?.keys.orEmpty() +
                    syncEvents.syncResponse.room?.leave?.keys.orEmpty()
        if (deleteReceiptsByRoomId.isNotEmpty()) {
            tm.writeTransaction {
                deleteReceiptsByRoomId.forEach {
                    roomUserStore.deleteReceiptsByRoomId(it)
                }
            }
        }
    }
}
