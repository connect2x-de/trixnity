package de.connect2x.trixnity.client.mocks

import de.connect2x.trixnity.client.key.KeyShareService
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId

class KeyShareServiceMock : KeyShareService {
    override suspend fun shareRoomKeyBundle(roomId: RoomId, userId: UserId): Result<Unit> {
        throw NotImplementedError()
    }
}
