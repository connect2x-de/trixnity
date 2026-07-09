package de.connect2x.trixnity.client.media

import de.connect2x.trixnity.client.media.mappings.EventContentMediaMapping.Companion.of
import de.connect2x.trixnity.client.media.mappings.EventContentMediaMappings
import de.connect2x.trixnity.client.media.mappings.EventContentMediaUploader
import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.client.media.mappings.findAndCallUploaderOrFallback
import de.connect2x.trixnity.client.media.mappings.findAndCallUriExtractorOrFallback
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

class EventContentMediaMappingsTest : TrixnityBaseTest() {
    val defaults = EventContentMediaMappings(
        listOf(
            of<FileBased.Audio>(
                uploader = null,
                uriExtractor = null
            ),
            of<FileBased.Audio>(
                uploader = AudioUploader(),
                uriExtractor = AudioExtractor()
            ),
            of<FileBased.File>(
                uploader = FileUploader(),
                uriExtractor = FileExtractor()
            ),
            of<FileBased.File>(
                uploader = FileUploader2(),
                uriExtractor = FileExtractor2()
            ),
            of<FileBased.Image>(
                uploader = null,
                uriExtractor = null
            )
        )
    )

    val uploadProgress: MutableStateFlow<FileTransferProgress?> = MutableStateFlow(null)
    val upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String =
        { _, _ -> "" }

    @Test
    fun `runs first found uploader matching the type`() = runTest {

        val content: RoomMessageEventContent = FileBased.File("")

        val newContent = defaults.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe FileBased.File("1")
        uris shouldBe setOf("File1")
    }

    @Test
    fun `runs fallback when no mapping matches the type`() = runTest {

        val content: RoomMessageEventContent =
            RoomMessageEventContent.TextBased.Text("What do you think about Cneoridium dumosum (Nuttall) Hooker F.?")

        val newContent = defaults.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe content
        uris shouldBe setOf()
    }

    @Test
    fun `ignores mappings without extractor or uploader as null`() = runTest {

        val content: RoomMessageEventContent = FileBased.Image("dinonuggers")

        val newContent = defaults.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe content
        uris shouldBe setOf()
    }

    @Test
    fun `runs the first nonnull result from matching mappings`() = runTest {

        val content: RoomMessageEventContent = FileBased.Audio("Blue-footed booby screeching")

        val newContent = defaults.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe FileBased.Audio("1")
        uris shouldBe setOf("Audio1")
    }

    @Test
    fun `runs supertype mapping if it comes before subtype`() = runTest {

        val defaults2 = EventContentMediaMappings(
            listOf(
                of<FileBased>(
                    uploader = FileBasedUploader(),
                    uriExtractor = FileBasedExtractor()
                ),
                of<FileBased.File>(
                    uploader = FileUploader(),
                    uriExtractor = FileExtractor()
                ),
            )
        )

        val content: FileBased.File = FileBased.File("")

        val newContent = defaults2.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults2.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe FileBased.Video("1")
        uris shouldBe setOf("FileBased1")
    }

    @Test
    fun `runs subtype mapping if it comes before supertype`() = runTest {

        val defaults2 = EventContentMediaMappings(
            listOf(
                of<FileBased.File>(
                    uploader = FileUploader(),
                    uriExtractor = FileExtractor()
                ),
                of<FileBased>(
                    uploader = FileBasedUploader(),
                    uriExtractor = FileBasedExtractor()
                ),
            )
        )

        val content: FileBased.File = FileBased.File("")

        val newContent = defaults2.findAndCallUploaderOrFallback(uploadProgress, content, upload)
        val uris = defaults2.findAndCallUriExtractorOrFallback(content)

        newContent shouldBe FileBased.File("1")
        uris shouldBe setOf("File1")
    }

    private class FileUploader : EventContentMediaUploader<FileBased.File> {
        override suspend fun invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: FileBased.File,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): FileBased.File {
            return FileBased.File("1")
        }
    }

    private class FileUploader2 : EventContentMediaUploader<FileBased.File> {
        override suspend fun invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: FileBased.File,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): FileBased.File {
            return FileBased.File("2")
        }
    }

    private class AudioUploader : EventContentMediaUploader<FileBased.Audio> {
        override suspend fun invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: FileBased.Audio,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): FileBased.Audio {
            return FileBased.Audio("1")
        }
    }

    private class FileBasedUploader : EventContentMediaUploader<FileBased> {
        override suspend fun invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: FileBased,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): FileBased {
            return FileBased.Video("1")
        }
    }

    private class FileExtractor : EventContentUriExtractor<FileBased.File> {
        override suspend fun invoke(
            content: FileBased.File,
        ): Set<String> {
            return setOf("File1")
        }
    }

    private class FileExtractor2 : EventContentUriExtractor<FileBased.File> {
        override suspend fun invoke(
            content: FileBased.File,
        ): Set<String> {
            return setOf("File2")
        }
    }

    private class AudioExtractor : EventContentUriExtractor<FileBased.Audio> {
        override suspend fun invoke(
            content: FileBased.Audio,
        ): Set<String> {
            return setOf("Audio1")
        }
    }

    private class FileBasedExtractor : EventContentUriExtractor<FileBased> {
        override suspend fun invoke(
            content: FileBased,
        ): Set<String> {
            return setOf("FileBased1")
        }
    }
}
