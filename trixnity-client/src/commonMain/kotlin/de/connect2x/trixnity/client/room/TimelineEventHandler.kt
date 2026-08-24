package de.connect2x.trixnity.client.room

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.client.store.RoomStore
import de.connect2x.trixnity.client.store.RoomTimelineStore
import de.connect2x.trixnity.client.store.StickyEventStore
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.client.store.TimelineEvent
import de.connect2x.trixnity.client.store.TimelineEventRelation
import de.connect2x.trixnity.client.store.eventId
import de.connect2x.trixnity.client.store.getNext
import de.connect2x.trixnity.client.store.getPrevious
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.clientserverapi.client.SyncEvents
import de.connect2x.trixnity.clientserverapi.model.room.GetEvents
import de.connect2x.trixnity.clientserverapi.model.user.Filters
import de.connect2x.trixnity.core.ClientEventEmitter.Priority
import de.connect2x.trixnity.core.EventHandler
import de.connect2x.trixnity.core.MSC4354
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.RoomEvent.MessageEvent
import de.connect2x.trixnity.core.model.events.RedactedEventContent
import de.connect2x.trixnity.core.model.events.UnsignedRoomEventData
import de.connect2x.trixnity.core.model.events.m.RelationType
import de.connect2x.trixnity.core.model.events.m.room.RedactionEventContent
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.core.unsubscribeOnCompletion
import de.connect2x.trixnity.utils.KeyedMutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

private val log = Logger("de.connect2x.trixnity.client.room.TimelineEventHandler")

interface TimelineEventHandler {
    /** Unsafe means, that it may throw exceptions */
    suspend fun unsafeFillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long = 20): Result<Unit>
}

@OptIn(MSC4354::class)
class TimelineEventHandlerImpl(
    private val api: MatrixClientServerApiClient,
    private val roomStore: RoomStore,
    private val roomTimelineStore: RoomTimelineStore,
    private val stickyEventStore: StickyEventStore,
    private val json: Json,
    private val mappings: EventContentSerializerMappings,
    private val config: MatrixClientConfiguration,
    private val tm: StoreTransactionManager,
) : EventHandler, TimelineEventHandler {
    override fun startInCoroutineScope(scope: CoroutineScope) {
        api.sync.subscribe(Priority.STORE_TIMELINE_EVENTS, ::handleSyncResponse).unsubscribeOnCompletion(scope)
    }

    private val timelineFilter by lazy {
        val baseFilter = config.syncFilter
        val filter =
            (baseFilter.room?.timeline ?: Filters.RoomFilter.RoomEventFilter()).copy(
                types = (mappings.message + mappings.state).map { it.type }.toSet()
            )
        json.encodeToString(filter)
    }

    private val timelineMutex = KeyedMutex<RoomId>()

    internal suspend fun handleSyncResponse(syncEvents: SyncEvents) {
        val syncResponse = syncEvents.syncResponse
        syncResponse.room?.join?.entries?.forEach { room ->
            val roomId = room.key
            room.value.timeline?.also {
                addEventsToTimelineAtEnd(
                    roomId = roomId,
                    newEvents = it.events,
                    previousBatch = it.previousBatch,
                    nextBatch = syncResponse.nextBatch,
                    hasGapBefore = it.limited == true,
                )
            }
        }
        syncResponse.room?.leave?.entries?.forEach { room ->
            room.value.timeline?.also {
                addEventsToTimelineAtEnd(
                    roomId = room.key,
                    newEvents = it.events,
                    previousBatch = it.previousBatch,
                    nextBatch = syncResponse.nextBatch,
                    hasGapBefore = it.limited == true,
                )
            }
        }
    }

    internal suspend fun addEventsToTimelineAtEnd(
        roomId: RoomId,
        newEvents: List<RoomEvent<*>>?,
        previousBatch: String?,
        nextBatch: String,
        hasGapBefore: Boolean,
    ) {
        timelineMutex.withLock(roomId) {
            val events = newEvents?.filterDuplicateEvents()?.handleRedactions()
            if (!events.isNullOrEmpty()) {
                log.debug { "add events to timeline at end of $roomId" }
                val lastEventId = roomStore.get(roomId).first()?.lastEventId
                val updatedAndNewTimelineEvents =
                    getUpdatedAndNewTimelineEvents(
                        startEvent =
                            TimelineEvent(
                                event = events.first(),
                                previousEventId = null,
                                nextEventId = null,
                                gap = null,
                            ),
                        roomId = roomId,
                        previousToken = previousBatch,
                        previousHasGap = hasGapBefore,
                        previousEvent = lastEventId,
                        previousEventChunk = null,
                        nextToken = nextBatch,
                        nextHasGap = true,
                        nextEvent = null,
                        nextEventChunk = events.drop(1),
                    )
                val timelineEventRelations = events.getTimelineEventRelations()
                tm.writeTransaction {
                    updatedAndNewTimelineEvents.forEach { roomTimelineStore.add(it) }
                    timelineEventRelations.forEach { roomTimelineStore.addRelation(it) }
                    roomStore.update(roomId) { it?.copy(lastEventId = events.last().id) }
                }
            }
        }
    }

    override suspend fun unsafeFillTimelineGaps(startEventId: EventId, roomId: RoomId, limit: Long): Result<Unit> =
        timelineMutex.withLock(roomId) {
            kotlin.runCatching {
                val isLastEventId = roomStore.get(roomId).first()?.lastEventId == startEventId

                val startEvent = roomTimelineStore.get(startEventId, roomId).first() ?: return@runCatching
                val previousToken: String?
                val previousHasGap: Boolean
                val previousEvent: EventId?
                val previousEventChunk: List<RoomEvent<*>>?
                val nextToken: String?
                val nextHasGap: Boolean
                val nextEvent: EventId?
                val nextEventChunk: List<RoomEvent<*>>?

                var insertNewEvents = false

                val startGap = startEvent.gap
                val startGapBatchBefore = startGap?.batchBefore
                val startGapBatchAfter = startGap?.batchAfter

                val possiblyPreviousEvent = roomTimelineStore.getPrevious(startEvent)
                if (startGapBatchBefore != null) {
                    insertNewEvents = true
                    log.debug { "fetch missing events before $startEventId" }
                    val destinationBatch = possiblyPreviousEvent?.gap?.batchAfter
                    val response =
                        api.room
                            .getEvents(
                                roomId = roomId,
                                from = startGapBatchBefore,
                                to = destinationBatch,
                                dir = GetEvents.Direction.BACKWARDS,
                                limit = limit,
                                filter = timelineFilter,
                            )
                            .getOrThrow()
                    previousToken = response.end?.takeIf { it != response.start } // detects start of timeline
                    previousEvent = possiblyPreviousEvent?.eventId
                    previousEventChunk = response.chunk?.filterDuplicateEvents()?.handleRedactions()
                    previousHasGap =
                        response.end != null &&
                            response.end != destinationBatch &&
                            response.chunk?.none { it.id == previousEvent } == true
                } else {
                    previousToken = null
                    previousEvent = possiblyPreviousEvent?.eventId
                    previousEventChunk = null
                    previousHasGap = false
                }

                val possiblyNextEvent = roomTimelineStore.getNext(startEvent)?.first()
                if (startGapBatchAfter != null && !isLastEventId) {
                    insertNewEvents = true
                    log.debug { "fetch missing events after $startEventId" }
                    val destinationBatch = possiblyNextEvent?.gap?.batchBefore
                    val response =
                        api.room
                            .getEvents(
                                roomId = roomId,
                                from = startGapBatchAfter,
                                to = destinationBatch,
                                dir = GetEvents.Direction.FORWARDS,
                                limit = limit,
                                filter = timelineFilter,
                            )
                            .getOrThrow()
                    nextToken = response.end
                    nextEvent = possiblyNextEvent?.eventId
                    nextEventChunk = response.chunk?.filterDuplicateEvents()?.handleRedactions()
                    nextHasGap =
                        response.end != null &&
                            response.end != destinationBatch &&
                            response.chunk?.none { it.id == nextEvent } == true
                } else {
                    nextToken = startGapBatchAfter
                    nextEvent = possiblyNextEvent?.eventId
                    nextEventChunk = null
                    nextHasGap = isLastEventId
                }

                if (insertNewEvents) {
                    val updatedAndNewTimelineEvents =
                        getUpdatedAndNewTimelineEvents(
                            startEvent = startEvent,
                            roomId = roomId,
                            previousToken = previousToken,
                            previousHasGap = previousHasGap,
                            previousEvent = previousEvent,
                            previousEventChunk = previousEventChunk,
                            nextToken = nextToken,
                            nextHasGap = nextHasGap,
                            nextEvent = nextEvent,
                            nextEventChunk = nextEventChunk,
                        )
                    val timelineEventRelations =
                        previousEventChunk?.getTimelineEventRelations().orEmpty() +
                            nextEventChunk?.getTimelineEventRelations().orEmpty()
                    tm.writeTransaction {
                        updatedAndNewTimelineEvents.forEach { roomTimelineStore.add(it) }
                        timelineEventRelations.forEach { roomTimelineStore.addRelation(it) }
                    }
                }
            }
        }

    private fun RoomEvent<*>.redact(because: MessageEvent<RedactionEventContent>): RoomEvent<RedactedEventContent> =
        when (this) {
            is MessageEvent -> {
                val redactedContent =
                    content as? RedactedEventContent
                        ?: RedactedEventContent(
                            api.eventContentSerializerMappings.message.find { it.kClass.isInstance(content) }?.type
                                ?: "UNKNOWN"
                        )
                MessageEvent(
                    redactedContent,
                    id,
                    sender,
                    roomId,
                    originTimestamp,
                    UnsignedRoomEventData.UnsignedMessageEventData(
                        redactedBecause = because,
                        transactionId = unsigned?.transactionId,
                    ),
                )
            }

            is RoomEvent.StateEvent -> {
                // TODO should update state to last known (maybe not needed with sync v3)
                val redactedContent =
                    content as? RedactedEventContent
                        ?: RedactedEventContent(
                            api.eventContentSerializerMappings.state.find { it.kClass.isInstance(content) }?.type
                                ?: "UNKNOWN"
                        )
                RoomEvent.StateEvent(
                    // TODO should keep some fields and change state: https://spec.matrix.org/v1.10/rooms/v9/#redactions
                    redactedContent,
                    id,
                    sender,
                    roomId,
                    originTimestamp,
                    UnsignedRoomEventData.UnsignedStateEventData(
                        redactedBecause = because,
                        transactionId = unsigned?.transactionId,
                    ),
                    stateKey,
                )
            }
        }

    internal suspend fun List<RoomEvent<*>>.handleRedactions(): List<RoomEvent<*>> {
        val redactionEvents =
            filter { it.content is RedactionEventContent }
                .filterIsInstance<MessageEvent<RedactionEventContent>>()
                .associateBy { it.content.redacts }
                .toMutableMap()

        if (redactionEvents.isEmpty()) return this

        val redactedRelations = mutableSetOf<TimelineEventRelation>()

        val eventsWithRedactedEvents = map { event ->
            val redactionEvent = redactionEvents[event.id]
            if (redactionEvent != null && redactionEvent != event) {
                log.debug { "redact event with id ${redactionEvent.content.redacts} in room ${redactionEvent.roomId}" }
                redactionEvents.remove(
                    event.id
                ) //  seeing the redacted event here means, there is no TimelineEvent yet that needs to be redacted
                event.getTimelineEventRelation()?.let { redactedRelations.add(it) }
                event.redact(redactionEvent)
            } else event
        }

        // redactionEvents and redactedRelations have been modified, so the order is important!

        redactedRelations.addAll(
            redactionEvents.values
                .mapNotNull { redactionEvent ->
                    roomTimelineStore.get(redactionEvent.content.redacts, redactionEvent.roomId).firstOrNull()?.event
                }
                .getTimelineEventRelations()
        )

        tm.writeTransaction {
            redactedRelations.forEach { roomTimelineStore.deleteRelation(it) }
            redactionEvents.values.forEach { redactionEvent ->
                val redactedEventId = redactionEvent.content.redacts
                val roomId = redactionEvent.roomId
                roomTimelineStore.deleteRelations(redactedEventId, roomId, RelationType.Replace)
                stickyEventStore.deleteByEventId(roomId, redactedEventId)
                roomTimelineStore.update(redactedEventId, roomId) { oldTimelineEvent ->
                    if (oldTimelineEvent != null) {
                        log.debug { "redact existing event with id $redactedEventId in room $roomId" }

                        val newEvent = oldTimelineEvent.event.redact(redactionEvent)
                        oldTimelineEvent.copy(event = newEvent, content = Result.success(newEvent.content))
                    } else {
                        log.trace {
                            "redact nothing because event with id $redactedEventId in room $roomId does not exist locally"
                        }
                        null
                    }
                }
            }
        }
        return eventsWithRedactedEvents
    }

    private fun RoomEvent<*>.getTimelineEventRelation(): TimelineEventRelation? {
        if (this !is MessageEvent<*>) return null
        val relatesTo = content.relatesTo ?: return null
        return TimelineEventRelation(
            roomId = roomId,
            eventId = id,
            relationType = relatesTo.relationType,
            relatedEventId = relatesTo.eventId,
        )
    }

    private fun Collection<RoomEvent<*>>.getTimelineEventRelations(): List<TimelineEventRelation> = mapNotNull {
        it.getTimelineEventRelation()
    }

    private suspend fun List<RoomEvent<*>>.filterDuplicateEvents() =
        distinctBy { it.id }.filter { roomTimelineStore.get(it.id, it.roomId).first() == null }

    internal suspend fun getUpdatedAndNewTimelineEvents(
        startEvent: TimelineEvent,
        roomId: RoomId,
        previousToken: String?,
        previousHasGap: Boolean,
        previousEvent: EventId?,
        previousEventChunk: List<RoomEvent<*>>?,
        nextToken: String?,
        nextHasGap: Boolean,
        nextEvent: EventId?,
        nextEventChunk: List<RoomEvent<*>>?,
    ): List<TimelineEvent> {
        log.trace {
            "addEventsToTimeline with parameters:\n" +
                "startEvent=${startEvent.eventId.full}\n" +
                "previousToken=$previousToken, previousHasGap=$previousHasGap, previousEvent=${previousEvent?.full}, previousEventChunk=${previousEventChunk?.map { it.id.full }}\n" +
                "nextToken=$nextToken, nextHasGap=$nextHasGap, nextEvent=${nextEvent?.full}, nextEventChunk=${nextEventChunk?.map { it.id.full }}"
        }

        val updatedPreviousEvent =
            if (previousEvent != null)
                roomTimelineStore.get(previousEvent, roomId).first()?.let { oldPreviousEvent ->
                    val oldGap = oldPreviousEvent.gap
                    oldPreviousEvent.copy(
                        nextEventId = previousEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                        gap = if (previousHasGap) oldGap else oldGap?.removeGapAfter(),
                    )
                }
            else null

        val updatedNextEvent =
            if (nextEvent != null)
                roomTimelineStore.get(nextEvent, roomId).first()?.let { oldNextEvent ->
                    val oldGap = oldNextEvent.gap
                    oldNextEvent.copy(
                        previousEventId = nextEventChunk?.lastOrNull()?.id ?: startEvent.eventId,
                        gap = if (nextHasGap) oldGap else oldGap?.removeGapBefore(),
                    )
                }
            else null

        val updatedStartEvent =
            roomTimelineStore.get(startEvent.eventId, roomId).first().let { oldStartEvent ->
                val hasGapBefore = previousEventChunk.isNullOrEmpty() && previousHasGap
                val hasGapAfter = nextEventChunk.isNullOrEmpty() && nextHasGap
                (oldStartEvent ?: startEvent).copy(
                    previousEventId = previousEventChunk?.firstOrNull()?.id ?: previousEvent,
                    nextEventId = nextEventChunk?.firstOrNull()?.id ?: nextEvent,
                    gap =
                        when {
                            hasGapBefore && hasGapAfter && previousToken != null && nextToken != null ->
                                TimelineEvent.Gap.GapBoth(previousToken, nextToken)

                            hasGapBefore && previousToken != null -> TimelineEvent.Gap.GapBefore(previousToken)
                            hasGapAfter && nextToken != null -> TimelineEvent.Gap.GapAfter(nextToken)
                            else -> null
                        },
                )
            }

        val newPreviousEvents =
            if (!previousEventChunk.isNullOrEmpty()) {
                log.debug { "add events to timeline of $roomId before ${startEvent.eventId}" }
                previousEventChunk.mapIndexed { index, event ->
                    when (index) {
                        previousEventChunk.lastIndex -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEvent,
                                nextEventId =
                                    if (index == 0) startEvent.eventId else previousEventChunk.getOrNull(index - 1)?.id,
                                gap =
                                    if (previousHasGap) previousToken?.let { TimelineEvent.Gap.GapBefore(it) } else null,
                            )
                        }

                        0 -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEventChunk.getOrNull(1)?.id,
                                nextEventId = startEvent.eventId,
                                gap = null,
                            )
                        }

                        else -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = previousEventChunk.getOrNull(index + 1)?.id,
                                nextEventId = previousEventChunk.getOrNull(index - 1)?.id,
                                gap = null,
                            )
                        }
                    }
                }
            } else emptyList()

        val newNextEvents =
            if (!nextEventChunk.isNullOrEmpty()) {
                log.debug { "add events to timeline of $roomId after ${startEvent.eventId}" }
                nextEventChunk.mapIndexed { index, event ->
                    when (index) {
                        nextEventChunk.lastIndex -> {
                            TimelineEvent(
                                event = event,
                                previousEventId =
                                    if (index == 0) startEvent.eventId else nextEventChunk.getOrNull(index - 1)?.id,
                                nextEventId = nextEvent,
                                gap = if (nextHasGap) nextToken?.let { TimelineEvent.Gap.GapAfter(it) } else null,
                            )
                        }

                        0 -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = startEvent.eventId,
                                nextEventId = nextEventChunk.getOrNull(1)?.id,
                                gap = null,
                            )
                        }

                        else -> {
                            TimelineEvent(
                                event = event,
                                previousEventId = nextEventChunk.getOrNull(index - 1)?.id,
                                nextEventId = nextEventChunk.getOrNull(index + 1)?.id,
                                gap = null,
                            )
                        }
                    }
                }
            } else emptyList()

        return listOfNotNull(updatedPreviousEvent, updatedNextEvent, updatedStartEvent) +
            newPreviousEvents +
            newNextEvents
    }
}
