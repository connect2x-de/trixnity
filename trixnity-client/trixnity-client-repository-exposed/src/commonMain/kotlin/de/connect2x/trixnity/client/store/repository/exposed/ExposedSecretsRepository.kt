package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredSecret
import de.connect2x.trixnity.client.store.repository.SecretsRepository
import de.connect2x.trixnity.crypto.SecretType
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedSecrets : LongIdTable("secrets") {
    val value = text("value")
}

internal class ExposedSecretsRepository(private val json: Json) : SecretsRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Map<SecretType, StoredSecret>? {
        return ExposedSecrets.selectAll()
            .where { ExposedSecrets.id eq key }
            .firstOrNull()
            ?.let { it[ExposedSecrets.value].let { outdated -> json.decodeFromString(outdated) } }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Map<SecretType, StoredSecret>) {
        ExposedSecrets.upsert {
            it[id] = key
            it[ExposedSecrets.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedSecrets.deleteWhere { ExposedSecrets.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedSecrets.deleteAll()
    }
}
