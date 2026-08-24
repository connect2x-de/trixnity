package de.connect2x.trixnity.crypto.driver.vodozemac.keys

import de.connect2x.trixnity.crypto.driver.keys.PickleKey
import de.connect2x.trixnity.vodozemac.PickleKey as Inner
import kotlin.jvm.JvmInline

@JvmInline value class VodozemacPickleKey(val inner: Inner) : PickleKey
