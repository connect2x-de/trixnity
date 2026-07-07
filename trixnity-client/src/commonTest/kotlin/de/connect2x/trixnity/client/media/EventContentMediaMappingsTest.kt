package de.connect2x.trixnity.client.media

import de.connect2x.trixnity.client.media.mappings.EventContentMediaMapping.Companion.of
import de.connect2x.trixnity.client.media.mappings.EventContentMediaMappings
import de.connect2x.trixnity.client.media.mappings.EventContentMediaUploader
import de.connect2x.trixnity.client.media.mappings.EventContentUriExtractor
import de.connect2x.trixnity.client.media.mappings.FallBackEventContentMediaUploader
import de.connect2x.trixnity.client.media.mappings.FallBackEventContentUriExtractor
import de.connect2x.trixnity.client.media.mappings.findUploaderOrFallback
import de.connect2x.trixnity.client.media.mappings.findUriExtractorOrFallback
import de.connect2x.trixnity.clientserverapi.model.media.FileTransferProgress
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent.TextBased
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

    @Test
    fun `returns the first matching result when multiple mappings exist`() = runTest {

        val content: FileBased.File = FileBased.File("")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe false
        (extractor is FallBackEventContentUriExtractor) shouldBe false

        (uploader is FileUploader) shouldBe true
        (extractor is FileExtractor) shouldBe true
    }

    @Test
    fun `returns uploader matching the runtime type even when referenced as supertype`() = runTest {

        val content: RoomMessageEventContent = FileBased.File("")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe false
        (extractor is FallBackEventContentUriExtractor) shouldBe false

        (uploader is FileUploader) shouldBe true
        (extractor is FileExtractor) shouldBe true
    }

    @Test
    fun `returns fallback when no mapping matches the runtime type`() = runTest {

        val content: TextBased.Text = TextBased.Text("hi")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe true
        (extractor is FallBackEventContentUriExtractor) shouldBe true
    }

    @Test
    fun `returns fallback when no mapping matches the runtime type passed as supertype`() = runTest {

        val content: RoomMessageEventContent = TextBased.Text("hi")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe true
        (extractor is FallBackEventContentUriExtractor) shouldBe true
    }

    @Test
    fun `ignores mappings without extractor or uploader as null`() = runTest {

        val content: RoomMessageEventContent = FileBased.Image("")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe true
        (extractor is FallBackEventContentUriExtractor) shouldBe true
    }

    @Test
    fun `returns the first nonnull result from matching mappings`() = runTest {

        val content: RoomMessageEventContent = FileBased.Audio("")

        val uploader = defaults.findUploaderOrFallback(content)
        val extractor = defaults.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe false
        (extractor is FallBackEventContentUriExtractor) shouldBe false

        (uploader is AudioUploader) shouldBe true
        (extractor is AudioExtractor) shouldBe true
    }

    @Test
    fun `returns supertype mapping when resolving subtype`() = runTest {

        val defaults2 = EventContentMediaMappings(
            listOf(
                of<FileBased>(
                    uploader = FileBasedUploader(),
                    uriExtractor = FileBasedExtractor()
                )
            )
        )

        val content: FileBased.File = FileBased.File("")

        val uploader = defaults2.findUploaderOrFallback(content)
        val extractor = defaults2.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe false
        (extractor is FallBackEventContentUriExtractor) shouldBe false

        (uploader is FileBasedUploader) shouldBe true
        (extractor is FileBasedExtractor) shouldBe true

        extractor(content) shouldBe setOf(null)
        uploader(MutableStateFlow(null), content) { _, _ -> "" }::class shouldBe FileBased.File::class
    }

    @Test
    fun `returns subtype mapping when resolving subtype and supertype also present`() = runTest {

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

        val uploader = defaults2.findUploaderOrFallback(content)
        val extractor = defaults2.findUriExtractorOrFallback(content)

        (uploader is FallBackEventContentMediaUploader) shouldBe false
        (extractor is FallBackEventContentUriExtractor) shouldBe false

        (uploader is FileUploader) shouldBe true
        (extractor is FileExtractor) shouldBe true
    }

    private class FileUploader : EventContentMediaUploader<FileBased.File> {
        override suspend fun <S : FileBased.File> invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: S,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): S {
            return content
        }
    }

    private class FileUploader2 : EventContentMediaUploader<FileBased.File> {
        override suspend fun <S : FileBased.File> invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: S,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): S {
            return content
        }
    }

    private class AudioUploader : EventContentMediaUploader<FileBased.Audio> {
        override suspend fun <S : FileBased.Audio> invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: S,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): S {
            return content
        }
    }

    private class FileBasedUploader : EventContentMediaUploader<FileBased> {
        override suspend fun <S : FileBased> invoke(
            uploadProgress: MutableStateFlow<FileTransferProgress?>,
            content: S,
            upload: suspend (cacheUri: String, uploadProgress: MutableStateFlow<FileTransferProgress?>) -> String
        ): S {
            return content
        }
    }

    private class FileExtractor : EventContentUriExtractor<FileBased.File> {
        override suspend fun invoke(
            content: FileBased.File,
        ): Set<String?> {
            return setOf(null)
        }
    }

    private class FileExtractor2 : EventContentUriExtractor<FileBased.File> {
        override suspend fun invoke(
            content: FileBased.File,
        ): Set<String?> {
            return setOf(null)
        }
    }

    private class AudioExtractor : EventContentUriExtractor<FileBased.Audio> {
        override suspend fun invoke(
            content: FileBased.Audio,
        ): Set<String?> {
            return setOf(null)
        }
    }

    private class FileBasedExtractor : EventContentUriExtractor<FileBased> {
        override suspend fun invoke(
            content: FileBased,
        ): Set<String?> {
            return setOf(null)
        }
    }
}
