package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.ServerData
import de.connect2x.trixnity.client.store.repository.ServerDataRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedServerData : LongIdTable("server_data") {
    val value = text("value")
}

internal class ExposedServerDataRepository(private val json: Json) : ServerDataRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): ServerData? {
        return ExposedServerData.selectAll().where { ExposedServerData.id eq key }.firstOrNull()?.let {
            it[ExposedServerData.value].let { outdated -> json.decodeFromString<ServerData>(outdated) }
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: ServerData) {
        ExposedServerData.upsert {
            it[id] = key
            it[ExposedServerData.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedServerData.deleteWhere { ExposedServerData.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedServerData.deleteAll()
    }
}
