package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.StoreTransactionManager.Companion.TRANSACTION_CHUNK_SIZE
import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.MessageEventContent
import de.connect2x.trixnity.core.subscribeEventList
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import kotlin.time.Clock
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@MSC4354
class StickyEventHandler(
    private val api: MatrixClientServerApiClient,
    private val stickyEventStore: StickyEventStore,
    private val clock: Clock,
    private val tm: StoreTransactionManager,
    private val config: MatrixClientConfiguration,
) : EventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        if (config.experimentalFeatures.enableMSC4354.not()) return
        api.sync
            .subscribeEventList(Priority.STORE_EVENTS, subscriber = ::setStickyEvents)
            .unsubscribeOnCompletion(scope)
        scope.launch { removeInvalidStickyEvents() }
    }

    internal suspend fun removeInvalidStickyEvents() {
        while (currentCoroutineContext().isActive) {
            delay(2.minutes)
            tm.writeTransaction { stickyEventStore.deleteInvalid() }
        }
    }

    internal suspend fun setStickyEvents(messageEvents: List<ClientEvent.RoomEvent.MessageEvent<MessageEventContent>>) {
        val now = clock.now()
        val stickyEventUpdates = messageEvents.mapNotNull { messageEvent ->
            if (messageEvent.sticky == null) return@mapNotNull null
            val stickyDurationRemaining =
                messageEvent.unsigned?.stickyDurationRemainingMs?.milliseconds?.coerceAtMost(1.hours)
                    ?: return@mapNotNull null
            if (stickyDurationRemaining <= ZERO) return@mapNotNull null
            StoredStickyEvent(
                roomId = messageEvent.roomId,
                eventId = messageEvent.id,
                endTime = now + stickyDurationRemaining,
            )
        }
        if (stickyEventUpdates.isNotEmpty()) {
            stickyEventUpdates.chunked(TRANSACTION_CHUNK_SIZE).forEach { chunk ->
                tm.writeTransaction { chunk.forEach { stickyEventStore.save(it) } }
            }
        }
    }
}
