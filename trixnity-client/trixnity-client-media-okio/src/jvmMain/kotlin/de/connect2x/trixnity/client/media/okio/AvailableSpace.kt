package de.connect2x.trixnity.client.media.okio

import java.io.File

actual suspend fun getPlatformAvailableSpace(): Long? {
    val file = File(".")
    val space = file.usableSpace
    return if (file.exists()) {
        space
    } else {
        null
    }
}
