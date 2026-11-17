package de.connect2x.trixnity.utils

import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class RetryFlowTest : TrixnityBaseTest() {

    @Test
    fun shouldEmitValuesWithoutRetry() = runTest {
        val result =
            retryFlow {
                    emit(1)
                    emit(2)
                    emit(3)
                }
                .take(3)
                .toList()

        result shouldBe listOf(1, 2, 3)
    }

    @Test
    fun shouldStopEmitValuesWhenFinished() = runTest {
        val result = backgroundScope.async {
            retryFlow {
                    emit(1)
                    emit(2)
                    emit(3)
                }
                .take(4)
                .toList()
        }
        delay(10.milliseconds)

        result.await() shouldBe listOf(1, 2, 3)
    }

    @Test
    fun shouldRetryOnException() = runTest {
        var attempts = 0
        val result =
            retryFlow {
                    attempts++
                    if (attempts < 3) {
                        throw RuntimeException("Test exception")
                    }
                    emit(attempts)
                }
                .take(3)
                .first()

        result shouldBe 3
        attempts shouldBe 3
    }

    @Test
    fun shouldRetryOnThrowable() = runTest {
        var attempts = 0
        val result =
            retryFlow {
                    attempts++
                    if (attempts < 3) {
                        throw Throwable("Test throwable")
                    }
                    emit(attempts)
                }
                .take(3)
                .first()

        result shouldBe 3
        attempts shouldBe 3
    }

    @Test
    fun shouldRespectDelayConfig() = runTest {
        var attempts = 0
        var result = 0

        backgroundScope.launch {
            delay(1.milliseconds)
            retryFlow(
                    scheduleBase = 1.minutes,
                    scheduleFactor = 2.0,
                    scheduleLimit = 5.minutes,
                    scheduleJitter = 1.0..1.0,
                ) {
                    attempts++
                    if (attempts < 4) {
                        throw RuntimeException("Test exception $attempts")
                    }
                    emit(attempts)
                }
                .collect {
                    result = it
                    delay(10.minutes)
                }
        }

        result shouldBe 0
        attempts shouldBe 0

        delay(1.minutes)
        result shouldBe 0
        attempts shouldBe 1

        delay(2.minutes)
        result shouldBe 0
        attempts shouldBe 2

        delay(4.minutes)
        result shouldBe 0
        attempts shouldBe 3

        delay(5.minutes)
        result shouldBe 4
        attempts shouldBe 4
    }

    @Test
    fun shouldPropagateExceptionOnCancellation() = runTest {
        shouldThrow<CancellationException> {
            retryFlow<Int> { throw CancellationException("Test cancellation") }.first()
        }
    }

    @Test
    fun shouldResetRetryCounterOnDelayConfigChange() = runTest {
        var attempts = 0
        var result = 0

        val configFlow =
            MutableStateFlow(
                RetryFlowDelayConfig.default.copy(
                    scheduleBase = 1.minutes,
                    scheduleFactor = 2.0,
                    scheduleLimit = 5.minutes,
                    scheduleJitter = 1.0..1.0,
                )
            )

        backgroundScope.launch {
            delay(1.milliseconds)
            retryFlow(configFlow) {
                    attempts++
                    if (attempts < 4) {
                        throw RuntimeException("Test exception $attempts")
                    }
                    emit(attempts)
                }
                .collect {
                    result = it
                    delay(10.minutes)
                }
        }

        result shouldBe 0
        attempts shouldBe 0

        delay(1.minutes)
        result shouldBe 0
        attempts shouldBe 1

        delay(2.minutes)
        result shouldBe 0
        attempts shouldBe 2

        delay(4.minutes)
        result shouldBe 0
        attempts shouldBe 3

        configFlow.value = RetryFlowDelayConfig.default.copy(scheduleBase = 10.milliseconds, scheduleJitter = 1.0..1.0)

        delay(1.milliseconds)
        result shouldBe 4
        attempts shouldBe 4
    }

    @Test
    fun shouldEmitValueAndRetryWhenExceptionThrownAfterEmit() = runTest {
        var attempts = 0
        val result =
            retryFlow(scheduleBase = 10.milliseconds, scheduleJitter = 1.0..1.0) {
                    attempts++
                    if (attempts < 3) {
                        emit(attempts)
                        throw RuntimeException("Test exception $attempts")
                    }
                    emit(attempts)
                }
                .toList()

        result shouldBe listOf(1, 2, 3)
        attempts shouldBe 3
    }

    @Test
    fun shouldKeepLoopingAndRetryInRetryLoop() = runTest {
        var calls = 0
        val job = backgroundScope.launch {
            retryLoop(scheduleBase = 100.milliseconds, scheduleJitter = 1.0..1.0) {
                calls++
                if (calls == 2) {
                    throw RuntimeException("Test exception")
                }
                delay(50.milliseconds)
            }
        }

        delay(1.milliseconds)
        calls shouldBe 1

        delay(50.milliseconds)
        calls shouldBe 2

        delay(50.milliseconds)
        calls shouldBe 2

        delay(50.milliseconds)
        calls shouldBe 3

        job.cancel()
    }

    @Test
    fun shouldPropagateExceptionOnCancellationInRetryLoop() = runTest {
        shouldThrow<CancellationException> { retryLoop { throw CancellationException("Test cancellation") } }
    }
}
