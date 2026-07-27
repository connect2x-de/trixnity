package de.connect2x.trixnity.client.media.okio

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSHomeDirectory

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getPlatformAvailableSpace(): Long? {
    val fileManager = NSFileManager.defaultManager
    val path = NSHomeDirectory()
    val attributes = fileManager.attributesOfFileSystemForPath(path, null)
    val freeSpace = attributes?.get(NSFileSystemFreeSize) as? Long
    return freeSpace
}
