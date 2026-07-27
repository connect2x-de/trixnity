package de.connect2x.trixnity.client.media.okio

import android.os.Build
import android.os.Environment
import android.os.StatFs

actual suspend fun getPlatformAvailableSpace(): Long? {
    return try {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            stat.availableBlocksLong * stat.blockSizeLong
        } else {
            @Suppress("DEPRECATION")
            stat.availableBlocks.toLong() * stat.blockSize.toLong()
        }
    } catch (e: Exception) {
        null
    }
}
