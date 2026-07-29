package de.connect2x.trixnity.client.media.okio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.linux.statvfs

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getPlatformAvailableSpace(): Long? {
    return memScoped {
        val stats = alloc<statvfs>()
        val success = statvfs(".", stats.ptr)
        if (success == 0) {
            val freeBlocks = stats.f_bavail.toLong()
            val blockSize = stats.f_frsize.toLong()
            freeBlocks * blockSize
        } else {
            null
        }
    }
}
