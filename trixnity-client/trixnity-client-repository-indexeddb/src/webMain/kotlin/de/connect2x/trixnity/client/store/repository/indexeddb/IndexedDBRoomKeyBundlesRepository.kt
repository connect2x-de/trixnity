package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.client.store.repository.RoomKeyBundlesRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import web.idb.IDBDatabase

internal class IndexedDBRoomKeyBundlesRepository(json: Json) :
    RoomKeyBundlesRepository,
    IndexedDBFullRepository<RoomId, StoredRoomKeyBundles>(
        objectStoreName = objectStoreName,
        keySerializer = { arrayOf(it.full) },
        valueSerializer = serializer(),
        json = json,
    ) {
    companion object {
        const val objectStoreName = "room_key_bundles"

        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 11) createIndexedDBMinimalStoreRepository(database, objectStoreName)
        }
    }
}
