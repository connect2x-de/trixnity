package de.connect2x.trixnity.client.room

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.mockMatrixClientServerApiClient
import de.connect2x.trixnity.client.store.RoomStateStore
import de.connect2x.trixnity.client.store.cache.ObservableCacheStatisticCollector
import de.connect2x.trixnity.client.store.repository.InMemoryRoomStateRepository
import de.connect2x.trixnity.client.store.repository.NoOpStoreTransactionManager
import de.connect2x.trixnity.client.store.repository.RoomStateRepository
import de.connect2x.trixnity.client.store.repository.RoomStateRepositoryKey
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.StateBaseEvent
import de.connect2x.trixnity.core.model.events.StateEventContent
import de.connect2x.trixnity.core.model.events.m.room.MemberEventContent
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.serialization.events.default
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import de.connect2x.trixnity.test.utils.scheduleSetup
import de.connect2x.trixnity.test.utils.testClock
import de.connect2x.trixnity.utils.WriteTransaction
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RoomStateEventHandlerTest : TrixnityBaseTest() {
    private val tm = NoOpStoreTransactionManager
    private val saveCalled = mutableListOf<StateBaseEvent<*>>().apply { scheduleSetup { clear() } }

    private class TestRoomStateRepository(
        val saveCalled: MutableList<StateBaseEvent<*>>,
        val delegate: InMemoryRoomStateRepository = InMemoryRoomStateRepository(),
    ) : RoomStateRepository by delegate {
        context(transaction: WriteTransaction)
        override suspend fun save(firstKey: RoomStateRepositoryKey, secondKey: String, value: StateBaseEvent<*>) {
            saveCalled.add(value)
            delegate.save(firstKey, secondKey, value)
        }
    }

    private val roomStateRepository = TestRoomStateRepository(saveCalled)

    private val roomStateStore =
        RoomStateStore(
                roomStateRepository,
                NoOpStoreTransactionManager,
                EventContentSerializerMappings.default,
                MatrixClientConfiguration(),
                ObservableCacheStatisticCollector(),
                testScope.backgroundScope,
                testScope.testClock,
            )
            .apply { scheduleSetup { init(backgroundScope) } }
    private val cut =
        RoomStateEventHandler(api = mockMatrixClientServerApiClient(), roomStateStore = roomStateStore, tm = tm)

    private fun stateEvent(content: StateEventContent, stateKey: String = "") =
        ClientEvent.RoomEvent.StateEvent(
            content = content,
            id = EventId("bla"),
            roomId = RoomId("room"),
            sender = UserId("user", "server"),
            originTimestamp = 1234,
            stateKey = stateKey,
            unsigned = null,
        )

    private fun strippedStateEvent(content: StateEventContent, stateKey: String = "") =
        ClientEvent.StrippedStateEvent(
            content = content,
            roomId = RoomId("room"),
            sender = UserId("user", "server"),
            stateKey = stateKey,
        )

    @Test
    fun `setState - deduplicate`() = runTest {
        val event1 = strippedStateEvent(NameEventContent("name1"))
        val event2 = stateEvent(MemberEventContent(membership = Membership.LEAVE), "@user2:server")
        val event3 = stateEvent(NameEventContent("name2"))
        val event4 = stateEvent(NameEventContent("name3"))
        val event5 = strippedStateEvent(MemberEventContent(membership = Membership.JOIN), "@user1:server")
        val event6 = stateEvent(NameEventContent("name4"))
        val event7 = stateEvent(MemberEventContent(membership = Membership.LEAVE), "@user1:server")
        val events: List<StateBaseEvent<*>> = listOf(event1, event2, event3, event4, event5, event6, event7)

        cut.setState(events)
        saveCalled shouldBe listOf(event2, event6, event7)
    }
}
