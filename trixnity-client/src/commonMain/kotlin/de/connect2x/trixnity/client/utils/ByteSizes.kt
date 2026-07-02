package de.connect2x.trixnity.client.utils

fun Long.gb() = mb() * 1_024

fun Long.mb() = kb() * 1_024

fun Long.kb() = this * 1_024

fun Int.gb() = toLong().gb()

fun Int.mb() = toLong().mb()

fun Int.kb() = toLong().kb()
