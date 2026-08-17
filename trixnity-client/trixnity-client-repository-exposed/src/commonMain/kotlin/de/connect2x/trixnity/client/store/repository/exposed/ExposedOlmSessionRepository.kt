package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OlmSessionRepository
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.crypto.olm.StoredOlmSession
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

internal object ExposedOlmSession : Table("olm_session") {
    val senderKey = varchar("sender_key", length = 255)
    override val primaryKey = PrimaryKey(senderKey)
    val value = text("value")
}

internal class ExposedOlmSessionRepository(private val json: Json) : OlmSessionRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Curve25519KeyValue): Set<StoredOlmSession>? {
        return ExposedOlmSession.selectAll().where { ExposedOlmSession.senderKey eq key.value }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedOlmSession.value]) }
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<Set<StoredOlmSession>> {
        return ExposedOlmSession.selectAll()
            .map { json.decodeFromString<Set<StoredOlmSession>>(it[ExposedOlmSession.value]) }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Curve25519KeyValue, value: Set<StoredOlmSession>) {
        ExposedOlmSession.upsert {
            it[senderKey] = key.value
            it[ExposedOlmSession.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Curve25519KeyValue) {
        ExposedOlmSession.deleteWhere { senderKey eq key.value }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedOlmSession.deleteAll()
    }
}
