package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmMessageIndexRepositoryKey
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmMessageIndex
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedInboundMegolmMessageIndex : Table("inbound_megolm_message_index") {
    val sessionId = varchar("session_id", length = 250)
    val roomId = varchar("room_id", length = 255)
    val messageIndex = long("message_index")
    override val primaryKey = PrimaryKey(sessionId, roomId, messageIndex)
    val eventId = text("event_id")
    val origin_timestamp = long("origin_timestamp")
}

internal class ExposedInboundMegolmMessageIndexRepository : InboundMegolmMessageIndexRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: InboundMegolmMessageIndexRepositoryKey): StoredInboundMegolmMessageIndex? =
        ExposedInboundMegolmMessageIndex.selectAll().where {
            ExposedInboundMegolmMessageIndex.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmMessageIndex.roomId.eq(key.roomId.full) and
                    ExposedInboundMegolmMessageIndex.messageIndex.eq(key.messageIndex)
        }.firstOrNull()?.let {
            StoredInboundMegolmMessageIndex(
                key.sessionId, key.roomId, key.messageIndex,
                EventId(it[ExposedInboundMegolmMessageIndex.eventId]),
                it[ExposedInboundMegolmMessageIndex.origin_timestamp]
            )
        }

    context(transaction: WriteTransaction)
    override suspend fun save(
        key: InboundMegolmMessageIndexRepositoryKey,
        value: StoredInboundMegolmMessageIndex
    ) {
        ExposedInboundMegolmMessageIndex.upsert {
            it[sessionId] = value.sessionId
            it[roomId] = value.roomId.full
            it[messageIndex] = value.messageIndex
            it[eventId] = value.eventId.full
            it[origin_timestamp] = value.originTimestamp
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: InboundMegolmMessageIndexRepositoryKey) {
        ExposedInboundMegolmMessageIndex.deleteWhere {
            sessionId.eq(key.sessionId) and
                    roomId.eq(key.roomId.full) and
                    messageIndex.eq(key.messageIndex)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedInboundMegolmMessageIndex.deleteAll()
    }
}
