package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredSecretKeyRequest
import de.connect2x.trixnity.client.store.repository.SecretKeyRequestRepository
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

internal object ExposedSecretKeyRequest : Table("secret_key_request") {
    val id = varchar("id", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedSecretKeyRequestRepository(private val json: Json) : SecretKeyRequestRepository {
    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredSecretKeyRequest> {
        return ExposedSecretKeyRequest.selectAll()
            .map { json.decodeFromString<StoredSecretKeyRequest>(it[ExposedSecretKeyRequest.value]) }
            .toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredSecretKeyRequest? {
        return ExposedSecretKeyRequest.selectAll().where { ExposedSecretKeyRequest.id eq key }.firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedSecretKeyRequest.value])
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredSecretKeyRequest) {
        ExposedSecretKeyRequest.upsert {
            it[id] = key
            it[ExposedSecretKeyRequest.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) {
        ExposedSecretKeyRequest.deleteWhere { id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedSecretKeyRequest.deleteAll()
    }
}
