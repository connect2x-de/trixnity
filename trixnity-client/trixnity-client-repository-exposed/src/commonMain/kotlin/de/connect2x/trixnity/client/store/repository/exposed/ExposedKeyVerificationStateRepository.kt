package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.KeyVerificationState
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateKey
import de.connect2x.trixnity.client.store.repository.KeyVerificationStateRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedKeyVerificationState : Table("key_verification_state") {
    val keyId = varchar("key_id", length = 255)
    val keyAlgorithm = varchar("key_algorithm", length = 255)
    override val primaryKey = PrimaryKey(keyId, keyAlgorithm)
    val verificationState = text("verification_state")
}

internal class ExposedKeyVerificationStateRepository(private val json: Json) : KeyVerificationStateRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: KeyVerificationStateKey): KeyVerificationState? {
        return ExposedKeyVerificationState.selectAll().where {
            ExposedKeyVerificationState.keyId.eq(key.keyId) and
                    ExposedKeyVerificationState.keyAlgorithm.eq(key.keyAlgorithm.name)
        }.firstOrNull()?.let {
            json.decodeFromString(it[ExposedKeyVerificationState.verificationState])
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: KeyVerificationStateKey, value: KeyVerificationState) {
        ExposedKeyVerificationState.upsert {
            it[keyId] = key.keyId
            it[keyAlgorithm] = key.keyAlgorithm.name
            it[verificationState] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: KeyVerificationStateKey) {
        ExposedKeyVerificationState.deleteWhere {
            keyId.eq(key.keyId) and
                    keyAlgorithm.eq(key.keyAlgorithm.name)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedKeyVerificationState.deleteAll()
    }
}
