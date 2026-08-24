package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.key.OutgoingRoomKeyRequestEventHandler
import de.connect2x.trixnity.core.model.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class OutgoingRoomKeyRequestEventHandlerMock : OutgoingRoomKeyRequestEventHandler {
    val requestRoomKeysCalled: MutableStateFlow<List<Pair<RoomId, String>>> = MutableStateFlow(listOf())

    override suspend fun requestRoomKeys(roomId: RoomId, sessionId: String) {
        requestRoomKeysCalled.update { it + (roomId to sessionId) }
    }
}
