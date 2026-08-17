package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.UserPresence
import de.connect2x.trixnity.client.store.repository.UserPresenceRepository
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

internal object ExposedUserPresence : Table("user_presence") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedUserPresenceRepository(private val json: Json) : UserPresenceRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): UserPresence? {
        return ExposedUserPresence.selectAll().where { ExposedUserPresence.userId eq key.full }.firstOrNull()
            ?.let {
                json.decodeFromString(it[ExposedUserPresence.value])
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(
        key: UserId,
        value: UserPresence
    ) {
        ExposedUserPresence.upsert {
            it[userId] = key.full
            it[ExposedUserPresence.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: UserId) {
        ExposedUserPresence.deleteWhere { userId eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedUserPresence.deleteAll()
    }
}
