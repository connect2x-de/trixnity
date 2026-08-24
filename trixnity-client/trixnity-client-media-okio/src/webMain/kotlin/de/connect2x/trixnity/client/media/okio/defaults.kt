package de.connect2x.trixnity.client.media.okio

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import okio.FileSystem
import okio.NodeJsFileSystem

internal actual val defaultFileSystem: FileSystem = NodeJsFileSystem
internal actual val ioContext: CoroutineContext = Dispatchers.Default
