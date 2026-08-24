package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.Room
import de.connect2x.trixnity.client.store.repository.RoomRepository
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

internal object ExposedRoom : Table("room") {
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(roomId)
    val value = text("value")
}

internal class ExposedRoomRepository(private val json: Json) : RoomRepository {
    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<Room> {
        return ExposedRoom.selectAll().map { json.decodeFromString<Room>(it[ExposedRoom.value]) }.toList()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): Room? {
        return ExposedRoom.selectAll()
            .where { ExposedRoom.roomId eq key.full }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedRoom.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: Room) {
        ExposedRoom.upsert {
            it[roomId] = key.full
            it[ExposedRoom.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) {
        ExposedRoom.deleteWhere { roomId eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoom.deleteAll()
    }
}
