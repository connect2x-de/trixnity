package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.ColumnTypeConverter
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.keys.KeyAlgorithm
import kotlin.time.Instant

internal object EventIdConverter {
    @ColumnTypeConverter fun from(string: String?): EventId? = string?.let(::EventId)

    @ColumnTypeConverter fun to(id: EventId?): String? = id?.full
}

internal object InstantConverter {
    @ColumnTypeConverter fun from(timeMs: Long?): Instant? = timeMs?.let(Instant::fromEpochMilliseconds)

    @ColumnTypeConverter fun to(instant: Instant?): Long? = instant?.toEpochMilliseconds()
}

internal object KeyAlgorithmConverter {
    @ColumnTypeConverter fun from(string: String?): KeyAlgorithm? = string?.let(KeyAlgorithm::of)

    @ColumnTypeConverter fun to(alg: KeyAlgorithm?): String? = alg?.name
}

internal object RelationTypeConverter {
    @ColumnTypeConverter fun from(string: String?): RelationType? = string?.let(RelationType::of)

    @ColumnTypeConverter fun to(id: RelationType?): String? = id?.name
}

internal object RoomIdConverter {
    @ColumnTypeConverter fun from(string: String?): RoomId? = string?.let(::RoomId)

    @ColumnTypeConverter fun to(id: RoomId?): String? = id?.full
}

internal object UserIdConverter {
    @ColumnTypeConverter fun from(string: String?): UserId? = string?.let(::UserId)

    @ColumnTypeConverter fun to(id: UserId?): String? = id?.full
}
