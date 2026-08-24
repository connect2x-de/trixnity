package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.utils.ReadTransaction

interface InboundMegolmSessionRepository :
    FullRepository<InboundMegolmSessionRepositoryKey, StoredInboundMegolmSession> {
    override fun serializeKey(key: InboundMegolmSessionRepositoryKey): String = key.roomId.full + key.sessionId

    context(transaction: ReadTransaction)
    suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession>
}

data class InboundMegolmSessionRepositoryKey(val sessionId: String, val roomId: RoomId)
