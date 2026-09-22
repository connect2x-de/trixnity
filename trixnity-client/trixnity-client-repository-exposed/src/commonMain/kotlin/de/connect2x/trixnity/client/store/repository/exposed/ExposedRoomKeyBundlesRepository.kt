package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredRoomKeyBundles
import de.connect2x.trixnity.client.store.repository.RoomKeyBundlesRepository
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedRoomKeyBundles : Table("room_key_bundles") {
    val id = varchar("id", length = 255)
    override val primaryKey = PrimaryKey(id)
    val value = text("value")
}

internal class ExposedRoomKeyBundlesRepository(private val json: Json) : RoomKeyBundlesRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: RoomId): StoredRoomKeyBundles? {
        return ExposedRoomKeyBundles.selectAll()
            .where { ExposedRoomKeyBundles.id eq key.full }
            .firstOrNull()
            ?.let { json.decodeFromString(it[ExposedRoomKeyBundles.value]) }
    }

    context(transaction: ReadTransaction)
    override suspend fun getAll(): List<StoredRoomKeyBundles> {
        return ExposedRoomKeyBundles.selectAll()
            .map { json.decodeFromString<StoredRoomKeyBundles>(it[ExposedRoomKeyBundles.value]) }
            .toList()
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: RoomId, value: StoredRoomKeyBundles) {
        ExposedRoomKeyBundles.upsert {
            it[id] = key.full
            it[ExposedRoomKeyBundles.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: RoomId) {
        ExposedRoomKeyBundles.deleteWhere { id eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedRoomKeyBundles.deleteAll()
    }
}
