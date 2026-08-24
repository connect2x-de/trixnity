package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredStickyEvent
import de.connect2x.trixnity.client.store.repository.StickyEventRepository
import de.connect2x.trixnity.client.store.repository.StickyEventRepositoryFirstKey
import de.connect2x.trixnity.client.store.repository.StickyEventRepositorySecondKey
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.StickyEventContent
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
internal object ExposedStickyEvent : Table("sticky_event") {
    val roomId = varchar("room_id", length = 255)
    val type = varchar("type", length = 255)
    val sender = varchar("sender", length = 255)
    val stickyKey = varchar("sticky_key", length = 255)
    val eventId = varchar("event_id", length = 255)
    override val primaryKey = PrimaryKey(roomId, type, sender, stickyKey)
    val endTimeMs = long("end_time_ms")
    val value = text("value")
}

@MSC4354
internal class ExposedStickyEventRepository(private val json: Json) : StickyEventRepository {
    companion object {
        private const val STICKY_KEY_NULL = "NULL"
        private const val STICKY_KEY_NON_NULL_PREFIX = "V-"

        private fun dbStickyKey(stickyKey: String?): String =
            stickyKey?.let { STICKY_KEY_NON_NULL_PREFIX + it } ?: STICKY_KEY_NULL

        private fun originalStickyKey(stickyKey: String): String? =
            if (stickyKey == STICKY_KEY_NULL) null else stickyKey.removePrefix(STICKY_KEY_NON_NULL_PREFIX)
    }

    context(transaction: ReadTransaction)
    override suspend fun get(
        firstKey: StickyEventRepositoryFirstKey
    ): Map<StickyEventRepositorySecondKey, StoredStickyEvent<StickyEventContent>> {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.roomId.eq(firstKey.roomId.full) and ExposedStickyEvent.type.eq(firstKey.type) }
            .associate {
                StickyEventRepositorySecondKey(
                    UserId(it[ExposedStickyEvent.sender]),
                    originalStickyKey(it[ExposedStickyEvent.stickyKey]),
                ) to json.decodeFromString(StoredStickyEvent.Serializer, it[ExposedStickyEvent.value])
            }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey,
    ): StoredStickyEvent<StickyEventContent>? {
        return ExposedStickyEvent.selectAll()
            .where {
                ExposedStickyEvent.roomId.eq(firstKey.roomId.full) and
                    ExposedStickyEvent.type.eq(firstKey.type) and
                    ExposedStickyEvent.sender.eq(secondKey.sender.full) and
                    ExposedStickyEvent.stickyKey.eq(dbStickyKey(secondKey.stickyKey))
            }
            .firstOrNull()
            ?.let { json.decodeFromString(StoredStickyEvent.Serializer, it[ExposedStickyEvent.value]) }
    }

    context(transaction: ReadTransaction)
    override suspend fun getByEndTimeBefore(
        before: Instant
    ): Set<Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>> {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.endTimeMs.less(before.toEpochMilliseconds()) }
            .map {
                StickyEventRepositoryFirstKey(RoomId(it[ExposedStickyEvent.roomId]), it[ExposedStickyEvent.type]) to
                    StickyEventRepositorySecondKey(
                        UserId(it[ExposedStickyEvent.sender]),
                        originalStickyKey(it[ExposedStickyEvent.stickyKey]),
                    )
            }
            .toSet()
    }

    context(transaction: ReadTransaction)
    override suspend fun getByEventId(
        roomId: RoomId,
        eventId: EventId,
    ): Pair<StickyEventRepositoryFirstKey, StickyEventRepositorySecondKey>? {
        return ExposedStickyEvent.selectAll()
            .where { ExposedStickyEvent.roomId.eq(roomId.full) and ExposedStickyEvent.eventId.eq(eventId.full) }
            .map {
                StickyEventRepositoryFirstKey(RoomId(it[ExposedStickyEvent.roomId]), it[ExposedStickyEvent.type]) to
                    StickyEventRepositorySecondKey(
                        UserId(it[ExposedStickyEvent.sender]),
                        originalStickyKey(it[ExposedStickyEvent.stickyKey]),
                    )
            }
            .firstOrNull()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(
        firstKey: StickyEventRepositoryFirstKey,
        secondKey: StickyEventRepositorySecondKey,
        value: StoredStickyEvent<StickyEventContent>,
    ) {
        ExposedStickyEvent.upsert {
            it[ExposedStickyEvent.roomId] = firstKey.roomId.full
            it[ExposedStickyEvent.type] = firstKey.type
            it[ExposedStickyEvent.sender] = secondKey.sender.full
            it[ExposedStickyEvent.stickyKey] = dbStickyKey(secondKey.stickyKey)
            it[ExposedStickyEvent.eventId] = value.event.id.full
            it[ExposedStickyEvent.endTimeMs] = value.endTime.toEpochMilliseconds()
            it[ExposedStickyEvent.value] = json.encodeToString(StoredStickyEvent.Serializer, value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: StickyEventRepositoryFirstKey, secondKey: StickyEventRepositorySecondKey) {
        ExposedStickyEvent.deleteWhere {
            ExposedStickyEvent.roomId.eq(firstKey.roomId.full) and
                ExposedStickyEvent.type.eq(firstKey.type) and
                ExposedStickyEvent.sender.eq(secondKey.sender.full) and
                ExposedStickyEvent.stickyKey.eq(dbStickyKey(secondKey.stickyKey))
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
