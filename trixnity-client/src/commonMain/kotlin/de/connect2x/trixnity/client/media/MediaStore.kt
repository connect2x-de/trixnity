package de.connect2x.trixnity.client.media

import de.connect2x.trixnity.utils.ByteArrayFlow
import kotlinx.coroutines.CoroutineScope

interface MediaStore {
    suspend fun init(coroutineScope: CoroutineScope) {}
    suspend fun addMedia(url: String, content: ByteArrayFlow)

    suspend fun getMedia(url: String): PlatformMedia?

    suspend fun deleteMedia(url: String)

    suspend fun changeMediaUrl(oldUrl: String, newUrl: String)
    suspend fun deleteAll()

    /**
     * Retrieves the available storage space for the device
     *
     * @return available space in bytes, or `null` if the value cannot be determined or an error occurs.
     */
    suspend fun getAvailableSpace(): Long?
}
