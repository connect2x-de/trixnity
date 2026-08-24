package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredNotification
import de.connect2x.trixnity.client.store.repository.NotificationRepository
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

internal object ExposedNotification : Table("notification") {
    val id = varchar("id", length = 255)
    val roomId = varchar("roomId", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedNotificationRepository(private val json: Json) : NotificationRepository {

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredNotification> {
        return ExposedNotification.selectAll()
            .map { json.decodeFromString<StoredNotification>(it[ExposedNotification.value]) }
            .toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): StoredNotification? {
        return ExposedNotification.selectAll()
            .where { ExposedNotification.id.eq(key) }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedNotification.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: StoredNotification) {
        ExposedNotification.upsert {
            it[roomId] = value.roomId.full
            it[id] = key
            it[ExposedNotification.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) {
        ExposedNotification.deleteWhere { id.eq(key) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedNotification.deleteAll()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedNotification.deleteWhere { ExposedNotification.roomId.eq(roomId.full) }
    }
}
