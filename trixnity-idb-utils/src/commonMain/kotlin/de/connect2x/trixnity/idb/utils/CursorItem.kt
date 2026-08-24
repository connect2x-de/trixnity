@file:OptIn(ExperimentalWasmJsInterop::class)

package de.connect2x.trixnity.idb.utils

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import web.idb.IDBValidKey

data class CursorItem(val primaryKey: IDBValidKey, val value: JsAny?) {
    companion object
}
