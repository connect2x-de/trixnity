package de.connect2x.trixnity.clientserverapi.client

import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadLimitExceededException(
    val maxSize: Long,
    message: String? = null,
    cause: Throwable? = null
) : IllegalStateException("File could not be downloaded because it would exceed the limit of $maxSize bytes"),
    CopyableThrowable<DownloadLimitExceededException> {
    override fun createCopy(): DownloadLimitExceededException {
        return DownloadLimitExceededException(maxSize, message, cause)
    }
}
