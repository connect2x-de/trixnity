package de.connect2x.trixnity.client.media.okio

import web.navigator.navigator
import web.storage.estimate

actual suspend fun getPlatformAvailableSpace(): Long? {
    return try {
        val estimate = navigator.storage.estimate()
        val quota = estimate.quota?.toLong() ?: 0L
        val usage = estimate.usage?.toLong() ?: 0L
        (quota - usage).coerceAtLeast(0L)
    } catch (e: Exception) {
        null
    }
}
