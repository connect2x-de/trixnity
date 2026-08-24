package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.RoomUser
import de.connect2x.trixnity.client.store.repository.RoomUserRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associateBy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedRoomUser : Table("room_user") {
    val userId = varchar("user_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(userId, roomId)
    val value = text("value")
}

internal class ExposedRoomUserRepository(private val json: Json) : RoomUserRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUser? {
        return ExposedRoomUser.selectAll()
            .where { ExposedRoomUser.roomId.eq(firstKey.full) and ExposedRoomUser.userId.eq(secondKey.full) }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedRoomUser.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUser) {
        ExposedRoomUser.upsert {
            it[roomId] = firstKey.full
            it[userId] = secondKey.full
            it[ExposedRoomUser.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: UserId) {
        ExposedRoomUser.deleteWhere { roomId.eq(firstKey.full) and userId.eq(secondKey.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomUser.deleteWhere { this.roomId.eq(roomId.full) }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUser> {
        return ExposedRoomUser.selectAll()
            .where { ExposedRoomUser.roomId eq firstKey.full }
            .map { json.decodeFromString<RoomUser>(it[ExposedRoomUser.value]) }
            .associateBy { it.userId }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomUser.deleteAll()
    }
}
