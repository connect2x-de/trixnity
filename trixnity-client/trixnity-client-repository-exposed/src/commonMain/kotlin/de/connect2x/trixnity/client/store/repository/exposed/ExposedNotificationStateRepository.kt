package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredNotificationState
import de.connect2x.trixnity.client.store.repository.NotificationStateRepository
import de.connect2x.trixnity.core.model.RoomId
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

internal object ExposedNotificationState : Table("notification_state") {
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedNotificationStateRepository(
    private val json: Json,
) : NotificationStateRepository {

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotificationState> {
        return ExposedNotificationState.selectAll()
            .map { json.decodeFromString<StoredNotificationState>(it[ExposedNotificationState.value]) }.toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredNotificationState? {
        return ExposedNotificationState.selectAll().where {
            ExposedNotificationState.roomId.eq(key.full)
        }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotificationState.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredNotificationState) {
        ExposedNotificationState.upsert {
            it[roomId] = key.full
            it[ExposedNotificationState.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) {
        ExposedNotificationState.deleteWhere { roomId.eq(key.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedNotificationState.deleteAll()
    }
}
