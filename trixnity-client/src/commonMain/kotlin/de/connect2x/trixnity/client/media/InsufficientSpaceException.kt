package de.connect2x.trixnity.client.media

class InsufficientSpaceException(val fileSize: Long, val availableSpace: Long) :
    IllegalStateException("Available space $availableSpace bytes insufficient for file with size $fileSize bytes")
