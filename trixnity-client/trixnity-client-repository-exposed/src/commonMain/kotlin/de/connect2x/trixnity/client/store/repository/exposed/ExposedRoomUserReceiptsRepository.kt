package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.RoomUserReceipts
import de.connect2x.trixnity.client.store.repository.RoomUserReceiptsRepository
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

internal object ExposedRoomUserReceipts : Table("room_user_receipts") {
    val userId = varchar("user_id", length = 255)
    val roomId = varchar("room_id", length = 255)
    override val primaryKey = PrimaryKey(userId, roomId)
    val value = text("value")
}

internal class ExposedRoomUserReceiptsRepository(private val json: Json) : RoomUserReceiptsRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: UserId): RoomUserReceipts? {
        return ExposedRoomUserReceipts.selectAll().where {
            ExposedRoomUserReceipts.roomId.eq(firstKey.full) and ExposedRoomUserReceipts.userId.eq(
                secondKey.full
            )
        }
            .firstOrNull()?.let {
                json.decodeFromString(it[ExposedRoomUserReceipts.value])
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: UserId, value: RoomUserReceipts) {
        ExposedRoomUserReceipts.upsert {
            it[roomId] = firstKey.full
            it[userId] = secondKey.full
            it[ExposedRoomUserReceipts.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: UserId) {
        ExposedRoomUserReceipts.deleteWhere { roomId.eq(firstKey.full) and userId.eq(secondKey.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedRoomUserReceipts.deleteWhere { this.roomId.eq(roomId.full) }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<UserId, RoomUserReceipts> {
        return ExposedRoomUserReceipts.selectAll().where { ExposedRoomUserReceipts.roomId eq firstKey.full }
            .map { json.decodeFromString<RoomUserReceipts>(it[ExposedRoomUserReceipts.value]) }
            .associateBy { it.userId }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomUserReceipts.deleteAll()
    }
}
