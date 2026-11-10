package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

@MSC4354
internal object ExposedStickyEvent : Table("sticky_event_2") {
    val roomId = varchar("room_id", length = 255)
    val eventId = varchar("event_id", length = 255)
    override val primaryKey = PrimaryKey(roomId, eventId)
    val endTimeMs = long("end_time_ms")
    val value = text("value")
}

@MSC4354
internal class ExposedStickyEventRepository(private val json: Json) : StickyEventRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<EventId, StoredStickyEvent> {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.roomId.eq(firstKey.full) }
            .associate {
                EventId(it[ExposedStickyEvent.eventId]) to json.decodeFromString(it[ExposedStickyEvent.value])
            }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: EventId): StoredStickyEvent? {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.roomId.eq(firstKey.full) and ExposedStickyEvent.eventId.eq(secondKey.full) }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedStickyEvent.value]) }
    }

    context(transaction: ReadTransaction)
    override suspend fun getByEndTimeBefore(before: Instant): Set<Pair<RoomId, EventId>> {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.endTimeMs.less(before.toEpochMilliseconds()) }
            .map { RoomId(it[ExposedStickyEvent.roomId]) to EventId(it[ExposedStickyEvent.eventId]) }
            .toSet()
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredStickyEvent> {
        return ExposedStickyEvent.selectAll()
            .map { json.decodeFromString<StoredStickyEvent>(it[ExposedStickyEvent.value]) }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: EventId, value: StoredStickyEvent) {
        ExposedStickyEvent.upsert {
            it[ExposedStickyEvent.roomId] = firstKey.full
            it[ExposedStickyEvent.eventId] = secondKey.full
            it[ExposedStickyEvent.endTimeMs] = value.endTime.toEpochMilliseconds()
            it[ExposedStickyEvent.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: EventId) {
        ExposedStickyEvent.deleteWhere {
            ExposedStickyEvent.roomId.eq(firstKey.full) and ExposedStickyEvent.eventId.eq(secondKey.full)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedStickyEvent.deleteAll()
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedStickyEvent.deleteWhere { ExposedStickyEvent.roomId.eq(roomId.full) }
    }
}
