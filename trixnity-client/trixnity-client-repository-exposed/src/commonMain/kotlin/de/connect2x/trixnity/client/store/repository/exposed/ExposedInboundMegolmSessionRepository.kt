package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedInboundMegolmSession : Table("inbound_megolm_session") {
    val senderKey = varchar("sender_key", length = 255)
    val sessionId = varchar("session_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(senderKey, sessionId, roomId)
    val firstKnownIndex = long("first_known_index")
    val hasBeenBackedUp = bool("has_been_backed_up")
    val isTrusted = bool("is_trusted")
    val senderSigningKey = text("sender_signing_key")
    val forwardingCurve25519KeyChain = text("forwarding_curve25519_key_chain")
    val pickled = text("pickled")
}

internal class ExposedInboundMegolmSessionRepository(private val json: Json) : InboundMegolmSessionRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? {
        return ExposedInboundMegolmSession.selectAll()
            .where {
                ExposedInboundMegolmSession.sessionId.eq(key.sessionId) and
                    ExposedInboundMegolmSession.roomId.eq(key.roomId.full)
            }
            .firstOrNull()
            ?.mapToStoredInboundMegolmSession()
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredInboundMegolmSession> {
        return ExposedInboundMegolmSession.selectAll().map { it.mapToStoredInboundMegolmSession() }.toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> {
        return ExposedInboundMegolmSession.selectAll()
            .where { ExposedInboundMegolmSession.hasBeenBackedUp.eq(false) }
            .map { it.mapToStoredInboundMegolmSession() }
            .toSet()
    }

    private fun ResultRow.mapToStoredInboundMegolmSession() =
        StoredInboundMegolmSession(
            senderKey = Curve25519KeyValue(this[ExposedInboundMegolmSession.senderKey]),
            sessionId = this[ExposedInboundMegolmSession.sessionId],
            roomId = RoomId(this[ExposedInboundMegolmSession.roomId]),
            firstKnownIndex = this[ExposedInboundMegolmSession.firstKnownIndex],
            hasBeenBackedUp = this[ExposedInboundMegolmSession.hasBeenBackedUp],
            isTrusted = this[ExposedInboundMegolmSession.isTrusted],
            senderSigningKey = Ed25519KeyValue(this[ExposedInboundMegolmSession.senderSigningKey]),
            forwardingCurve25519KeyChain =
                json.decodeFromString(this[ExposedInboundMegolmSession.forwardingCurve25519KeyChain]),
            pickled = this[ExposedInboundMegolmSession.pickled],
        )

    context(transaction: WriteTransaction)
    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) {
        ExposedInboundMegolmSession.upsert {
            it[senderKey] = value.senderKey.value
            it[sessionId] = value.sessionId
            it[roomId] = value.roomId.full
            it[firstKnownIndex] = value.firstKnownIndex
            it[hasBeenBackedUp] = value.hasBeenBackedUp
            it[isTrusted] = value.isTrusted
            it[senderSigningKey] = value.senderSigningKey.value
            it[forwardingCurve25519KeyChain] = json.encodeToString(value.forwardingCurve25519KeyChain)
            it[pickled] = value.pickled
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) {
        ExposedInboundMegolmSession.deleteWhere { sessionId.eq(key.sessionId) and roomId.eq(key.roomId.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedInboundMegolmSession.deleteAll()
    }
}
