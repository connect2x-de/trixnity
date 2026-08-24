package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OutdatedKeysRepository
import de.connect2x.trixnity.core.model.UserId
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

internal object ExposedOutdatedKeys : LongIdTable("outdated_keys") {
    val value = text("value")
}

internal class ExposedOutdatedKeysRepository(private val json: Json) : OutdatedKeysRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Set<UserId>? {
        return ExposedOutdatedKeys.selectAll()
            .where { ExposedOutdatedKeys.id eq key }
            .firstOrNull()
            ?.let { it[ExposedOutdatedKeys.value].let { outdated -> json.decodeFromString<Set<UserId>>(outdated) } }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Set<UserId>) {
        ExposedOutdatedKeys.upsert {
            it[id] = key
            it[ExposedOutdatedKeys.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedOutdatedKeys.deleteWhere { ExposedOutdatedKeys.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedOutdatedKeys.deleteAll()
    }
}
