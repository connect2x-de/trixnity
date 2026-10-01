package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.utils.ReadTransaction

interface InboundMegolmSessionRepository : MapRepository<RoomId, String, StoredInboundMegolmSession> {
    override fun serializeKey(firstKey: RoomId, secondKey: String): String = firstKey.full + secondKey

    context(transaction: ReadTransaction)
    suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession>

    context(transaction: ReadTransaction)
    suspend fun getAll(): Set<StoredInboundMegolmSession>
}
