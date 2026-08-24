package de.connect2x.trixnity.crypto.core

import de.connect2x.trixnity.utils.ByteArrayFlow
import kotlinx.coroutines.flow.filterNot

fun ByteArrayFlow.filterNotEmpty() = filterNot { it.isEmpty() }
