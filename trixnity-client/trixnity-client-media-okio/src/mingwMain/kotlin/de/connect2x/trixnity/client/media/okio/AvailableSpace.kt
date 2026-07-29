package de.connect2x.trixnity.client.media.okio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.GetDiskFreeSpaceExW
import platform.windows.ULARGE_INTEGER

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getPlatformAvailableSpace(): Long? {
    return memScoped {
        val freeBytesAvailable = alloc<ULARGE_INTEGER>()
        val totalBytes = alloc<ULARGE_INTEGER>()
        val totalFreeBytes = alloc<ULARGE_INTEGER>()

        val success = GetDiskFreeSpaceExW(
            null,
            freeBytesAvailable.ptr,
            totalBytes.ptr,
            totalFreeBytes.ptr
        )

        if (success != 0) {
            freeBytesAvailable.QuadPart.toLong()
        } else {
            null
        }
    }
}
