package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepository
import de.connect2x.trixnity.client.store.repository.InboundMegolmSessionRepositoryKey
import de.connect2x.trixnity.crypto.olm.StoredInboundMegolmSession
import de.connect2x.trixnity.idb.utils.KeyPath
import de.connect2x.trixnity.idb.utils.WrappedTransaction
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
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
                this@IndexedDBInboundMegolmSessionRepository.serializeKey(key)
        }

    companion object {
        const val objectStoreName = "inbound_megolm_session"

        fun WrappedTransaction.migrate(database: IDBDatabase, oldVersion: Int) {
            if (oldVersion < 1) {
                createObjectStore(database, objectStoreName).apply {
                    createIndex("hasBeenBackedUp", KeyPath.Single("hasBeenBackedUp"), unique = false)
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
    override suspend fun get(key: InboundMegolmSessionRepositoryKey): StoredInboundMegolmSession? =
        internalRepository.get(key)?.toStoredInboundMegolmSession()

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredInboundMegolmSession> =
        internalRepository.getAll().map { it.toStoredInboundMegolmSession() }

    context(transaction: WriteTransaction)
    override suspend fun save(key: InboundMegolmSessionRepositoryKey, value: StoredInboundMegolmSession) =
        internalRepository.save(key, value.toIndexedDBInboundMegolmSession())

    context(transaction: WriteTransaction)
    override suspend fun delete(key: InboundMegolmSessionRepositoryKey) = internalRepository.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = internalRepository.deleteAll()
}
