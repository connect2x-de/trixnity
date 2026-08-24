package de.connect2x.trixnity.client.media.okio

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

internal actual val defaultFileSystem: FileSystem = FileSystem.SYSTEM
internal actual val ioContext: CoroutineContext = Dispatchers.IO
