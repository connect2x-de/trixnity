package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.core.model.RoomId

interface RoomKeyBundlesRepository : FullRepository<RoomId, StoredRoomKeyBundles> {
    override fun serializeKey(key: RoomId): String = key.full
}
