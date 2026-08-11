package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationKey
import de.connect2x.trixnity.client.store.repository.TimelineEventRelationRepository
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedTimelineEventRelation : Table("room_timeline_event_relation") {
    val roomId = varchar("room_id", length = 128)
    val eventId = varchar("event_id", length = 128)
    val relationType = varchar("relation_type", length = 128)
    val relatedEventId = varchar("related_event_id", length = 128)
    override val primaryKey = PrimaryKey(roomId, eventId, relationType, relatedEventId)
}

internal class ExposedTimelineEventRelationRepository : TimelineEventRelationRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: TimelineEventRelationKey): Map<EventId, TimelineEventRelation> {
        return ExposedTimelineEventRelation.selectAll().where {
            ExposedTimelineEventRelation.relatedEventId.eq(firstKey.relatedEventId.full) and
                    ExposedTimelineEventRelation.roomId.eq(firstKey.roomId.full) and
                    ExposedTimelineEventRelation.relationType.eq(firstKey.relationType.name)
        }.associate {
            val eventId = EventId(it[ExposedTimelineEventRelation.eventId])
            eventId to TimelineEventRelation(
                roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                eventId = eventId,
                relationType = RelationType.of(it[ExposedTimelineEventRelation.relationType]),
                relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
            )
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteByRoomId(roomId: RoomId) {
        ExposedTimelineEventRelation.deleteWhere { ExposedTimelineEventRelation.roomId.eq(roomId.full) }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId
    ): TimelineEventRelation? {
        return ExposedTimelineEventRelation.selectAll().where {
            ExposedTimelineEventRelation.relatedEventId.eq(firstKey.relatedEventId.full) and
                    ExposedTimelineEventRelation.roomId.eq(firstKey.roomId.full) and
                    ExposedTimelineEventRelation.relationType.eq(firstKey.relationType.name) and
                    ExposedTimelineEventRelation.eventId.eq(secondKey.full)
        }.firstOrNull()?.let {
            TimelineEventRelation(
                roomId = RoomId(it[ExposedTimelineEventRelation.roomId]),
                eventId = EventId(it[ExposedTimelineEventRelation.eventId]),
                relationType = RelationType.of(it[ExposedTimelineEventRelation.relationType]),
                relatedEventId = EventId(it[ExposedTimelineEventRelation.relatedEventId]),
            )
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(
        firstKey: TimelineEventRelationKey,
        secondKey: EventId,
        value: TimelineEventRelation
    ) {
        ExposedTimelineEventRelation.upsert {
            it[ExposedTimelineEventRelation.eventId] = value.eventId.full
            it[ExposedTimelineEventRelation.roomId] = value.roomId.full
            it[ExposedTimelineEventRelation.relationType] = value.relationType.name
            it[ExposedTimelineEventRelation.relatedEventId] = value.relatedEventId.full
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: TimelineEventRelationKey, secondKey: EventId) {
        ExposedTimelineEventRelation.deleteWhere {
            relatedEventId.eq(firstKey.relatedEventId.full) and
                    roomId.eq(firstKey.roomId.full) and
                    relationType.eq(firstKey.relationType.name) and
                    eventId.eq(secondKey.full)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedTimelineEventRelation.deleteAll()
    }
}
