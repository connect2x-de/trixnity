package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredNotificationUpdate
import de.connect2x.trixnity.client.store.repository.NotificationUpdateRepository
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

internal object ExposedNotificationUpdate : Table("notification_update") {
    val id = varchar("id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedNotificationUpdateRepository(
    private val json: Json,
) : NotificationUpdateRepository {

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotificationUpdate> {
        return ExposedNotificationUpdate.selectAll()
            .map { json.decodeFromString<StoredNotificationUpdate>(it[ExposedNotificationUpdate.value]) }.toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredNotificationUpdate? {
        return ExposedNotificationUpdate.selectAll().where {
            ExposedNotificationUpdate.id.eq(key)
        }.firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotificationUpdate.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredNotificationUpdate) {
        ExposedNotificationUpdate.upsert {
            it[roomId] = value.roomId.full
            it[id] = key
            it[ExposedNotificationUpdate.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) {
        ExposedNotificationUpdate.deleteWhere { id.eq(key) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedNotificationUpdate.deleteAll()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedNotificationUpdate.deleteWhere { ExposedNotificationUpdate.roomId.eq(roomId.full) }
    }
}
