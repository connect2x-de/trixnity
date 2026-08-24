package de.connect2x.trixnity.utils

import java.nio.ByteBuffer
import kotlinx.coroutines.flow.flowOf

fun ByteBuffer.toByteArrayFlow(): ByteArrayFlow {
    return flowOf(array().copyOf())
}
