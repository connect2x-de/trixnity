package de.connect2x.trixnity.utils

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

internal actual val ioContext: CoroutineContext = Dispatchers.IO
