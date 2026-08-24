package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredCrossSigningKeys
import de.connect2x.trixnity.client.store.repository.CrossSigningKeysRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedCrossSigningKeys : Table("cross_signing_keys") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedCrossSigningKeysRepository(private val json: Json) : CrossSigningKeysRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): Set<StoredCrossSigningKeys>? {
        return ExposedCrossSigningKeys.selectAll()
            .where { ExposedCrossSigningKeys.userId eq key.full }
            .firstOrNull()
            ?.let {
                it[ExposedCrossSigningKeys.value].let { deviceKeys ->
                    json.decodeFromString<Set<StoredCrossSigningKeys>>(deviceKeys)
                }
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: UserId, value: Set<StoredCrossSigningKeys>) {
        ExposedCrossSigningKeys.upsert {
            it[userId] = key.full
            it[ExposedCrossSigningKeys.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: UserId) {
        ExposedCrossSigningKeys.deleteWhere { userId eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedCrossSigningKeys.deleteAll()
    }
}
