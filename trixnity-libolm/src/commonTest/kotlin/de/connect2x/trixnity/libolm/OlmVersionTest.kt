package de.connect2x.trixnity.libolm

import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class OlmVersionTest {
    @Test
    fun versionShouldBeSet() = runTest {
        getOlmVersion().major shouldBeGreaterThan 0
        getOlmVersion().minor shouldBeGreaterThan 0
        getOlmVersion().patch shouldBeGreaterThan 0
    }
}
