package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.repository.TimelineEventKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRepository
import de.connect2x.trixnity.core.model.RoomId
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

internal object ExposedTimelineEvent : Table("room_timeline_event") {
    val roomId = varchar("room_id", length = 255)
    val eventId = varchar("event_id", length = 255)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val value = text("value")
}

internal class ExposedTimelineEventRepository(private val json: Json) : TimelineEventRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: TimelineEventKey): TimelineEvent? {
        return ExposedTimelineEvent.selectAll()
            .where {
                ExposedTimelineEvent.eventId.eq(key.eventId.full) and ExposedTimelineEvent.roomId.eq(key.roomId.full)
            }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedTimelineEvent.value]) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedTimelineEvent.deleteWhere { ExposedTimelineEvent.roomId.eq(roomId.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: TimelineEventKey, value: TimelineEvent) {
        ExposedTimelineEvent.upsert {
            it[eventId] = key.eventId.full
            it[roomId] = key.roomId.full
            it[ExposedTimelineEvent.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: TimelineEventKey) {
        ExposedTimelineEvent.deleteWhere { eventId.eq(key.eventId.full) and roomId.eq(key.roomId.full) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedTimelineEvent.deleteAll()
    }
}
