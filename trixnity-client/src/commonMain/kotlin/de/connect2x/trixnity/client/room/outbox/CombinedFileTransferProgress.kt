package de.connect2x.trixnity.client.room.outbox

import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress

internal class CombinedFileTransferProgress(private val onProgress: (FileTransferProgress?) -> Unit) {
    private val progresses = mutableListOf<FileTransferProgress?>()

    fun acquire(initialProgress: FileTransferProgress?): (FileTransferProgress) -> Unit {
        val index = progresses.size
        progresses.add(initialProgress)
        return { progress ->
            progresses[index] = progress
            publish()
        }
    }

    fun publish() {
        val combined =
            progresses.filterNotNull().reduceOrNull { accumulated, progress ->
                val total =
                    if (accumulated.total != null || progress.total != null) {
                        (accumulated.total ?: 0) + (progress.total ?: 0)
                    } else null
                FileTransferProgress(accumulated.transferred + progress.transferred, total)
            }
        onProgress(combined)
    }
}
