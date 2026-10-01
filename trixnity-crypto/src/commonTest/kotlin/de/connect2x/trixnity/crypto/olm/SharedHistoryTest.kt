package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SharedHistoryTest : TrixnityBaseTest() {

    @Test
    fun `false when HistoryVisibility JOINED`() {
        HistoryVisibility.JOINED.sharedHistory shouldBe false
    }

    @Test
    fun `false when HistoryVisibility INVITED`() {
        HistoryVisibility.INVITED.sharedHistory shouldBe false
    }

    @Test
    fun `true when HistoryVisibility SHARED`() {
        HistoryVisibility.SHARED.sharedHistory shouldBe true
    }

    @Test
    fun `true when HistoryVisibility WORLD_READABLE`() {
        HistoryVisibility.WORLD_READABLE.sharedHistory shouldBe true
    }

    @Test
    fun `true when HistoryVisibility null`() {
        val historyVisibility: HistoryVisibility? = null
        historyVisibility.sharedHistory shouldBe true
    }
}
