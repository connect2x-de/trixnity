package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import web.idb.IDBDatabase
import web.idb.IDBValidKey

@Serializable data class IndexedDBInboundMegolmSession(val value: StoredInboundMegolmSession, val hasBeenBackedUp: Int)

fun IndexedDBInboundMegolmSession.toStoredInboundMegolmSession() = value

fun StoredInboundMegolmSession.toIndexedDBInboundMegolmSession() =
    IndexedDBInboundMegolmSession(value = this, hasBeenBackedUp = if (hasBeenBackedUp) 1 else 0)

internal class IndexedDBInboundMegolmSessionRepository(private val json: Json) :
    InboundMegolmSessionRepository, IndexedDBRepository(objectStoreName) {

    private data class InboundMegolmSessionRepositoryKey(val sessionId: String, val roomId: RoomId)

    // We need this, because hasBeenBackedUp cannot be indexed as boolean.
    private val internalRepository =
        object :
            IndexedDBFullRepository<InboundMegolmSessionRepositoryKey, IndexedDBInboundMegolmSession>(
                objectStoreName = objectStoreName,
                keySerializer = { arrayOf(it.roomId.full, it.sessionId) },
                valueSerializer = serializer(),
                json = json,
            ) {
            override fun serializeKey(key: InboundMegolmSessionRepositoryKey): String =
                this@IndexedDBInboundMegolmSessionRepository.serializeKey(key.roomId, key.sessionId)
        }

    companion object {
        const val objectStoreName = "inbound_megolm_session"

        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) {
                createObjectStore(database, objectStoreName).apply {
                    createIndex("hasBeenBackedUp", KeyPath.Single("hasBeenBackedUp"), unique = false)
                }
            }
            if (oldVersion < 11) {
                objectStore(objectStoreName).apply {
                    createIndex("roomId", KeyPath.Single("value.roomId"), unique = false)
                }
            }
        }
    }

    context(transaction: ReadTransaction)
    override suspend fun getByNotBackedUp(): Set<StoredInboundMegolmSession> = withRead { store ->
        store
            .index("hasBeenBackedUp")
            .openCursor(IDBValidKey(0))
            .mapNotNull { json.decodeFromDynamicNullable(internalRepository.valueSerializer, it.value) }
            .map { it.toStoredInboundMegolmSession() }
            .toSet()
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId): Map<String, StoredInboundMegolmSession> = withRead { store ->
        store
            .index("roomId")
            .openCursor(IDBValidKey(firstKey.full))
            .mapNotNull { json.decodeFromDynamicNullable(internalRepository.valueSerializer, it.value) }
            .map { it.toStoredInboundMegolmSession() }
            .associate { it.sessionId to it }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: RoomId, secondKey: String): StoredInboundMegolmSession? =
        internalRepository.get(InboundMegolmSessionRepositoryKey(secondKey, firstKey))?.toStoredInboundMegolmSession()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): Set<StoredInboundMegolmSession> =
        internalRepository.getAll().map { it.toStoredInboundMegolmSession() }.toSet()

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: RoomId, secondKey: String, value: StoredInboundMegolmSession) =
        internalRepository.save(
            InboundMegolmSessionRepositoryKey(secondKey, firstKey),
            value.toIndexedDBInboundMegolmSession(),
        )

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: RoomId, secondKey: String) =
        internalRepository.delete(InboundMegolmSessionRepositoryKey(secondKey, firstKey))

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = internalRepository.deleteAll()
}
