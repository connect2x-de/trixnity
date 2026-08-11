package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OutboundMegolmSessionRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredOutboundMegolmSession
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedOutboundMegolmSession : Table("outbound_megolm_session") {
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedOutboundMegolmSessionRepository(private val json: Json) : OutboundMegolmSessionRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredOutboundMegolmSession? {
        return ExposedOutboundMegolmSession.selectAll().where { ExposedOutboundMegolmSession.roomId eq key.full }
            .firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedOutboundMegolmSession.value])
            }
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredOutboundMegolmSession> {
        return ExposedOutboundMegolmSession.selectAll()
            .map { json.decodeFromString<StoredOutboundMegolmSession>(it[ExposedOutboundMegolmSession.value]) }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredOutboundMegolmSession) {
        ExposedOutboundMegolmSession.upsert {
            it[roomId] = key.full
            it[ExposedOutboundMegolmSession.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) {
        ExposedOutboundMegolmSession.deleteWhere { roomId eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedOutboundMegolmSession.deleteAll()
    }
}
