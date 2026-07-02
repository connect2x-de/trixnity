package de.connect2x.trixnity.clientserverapi.client

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

class DownloadLimitExceededException(fileSizeLimit: Long) : IllegalStateException("File could not be downloaded because it would exceed the limit of ${fileSizeLimit.toHumanReadableSize()}")

fun Long.toHumanReadableSize(): String {
    if (this < 1024) return "$this B"
    val exp = (ln(this.toDouble()) / ln(1024.0)).toInt()
    val unit = arrayOf("KB", "MB", "GB", "TB", "PB")[exp - 1]
    val value = this / 1024.0.pow(exp.toDouble())

    val rounded = (value * 100).roundToLong()
    val integer = rounded/100
    val fraction = rounded % 100

    return "$integer.$fraction $unit"
}
