package de.connect2x.trixnity.test.utils

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime

@OptIn(ExperimentalCoroutinesApi::class)
val TestScope.testClock: Clock
    get() =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
        }
