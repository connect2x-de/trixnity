package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility

val HistoryVisibility?.sharedHistory: Boolean
    get() =
        when (this) {
            HistoryVisibility.JOINED,
            HistoryVisibility.INVITED -> false
            HistoryVisibility.SHARED,
            HistoryVisibility.WORLD_READABLE,
            null -> true
        }
