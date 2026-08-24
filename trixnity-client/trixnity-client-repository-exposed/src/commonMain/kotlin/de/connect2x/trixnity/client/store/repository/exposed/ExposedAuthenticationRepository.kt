package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.Authentication
import de.connect2x.trixnity.client.store.repository.AuthenticationRepository
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

internal object ExposedAuthentication : LongIdTable("authentication") {
    val value = text("value").nullable()
}

internal class ExposedAuthenticationRepository(private val json: Json) : AuthenticationRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Authentication? {
        return ExposedAuthentication.selectAll()
            .where { ExposedAuthentication.id eq key }
            .firstOrNull()
            ?.get(ExposedAuthentication.value)
            ?.let { json.decodeFromString(it) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Authentication) {
        ExposedAuthentication.upsert {
            it[id] = key
            it[ExposedAuthentication.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedAuthentication.deleteWhere { ExposedAuthentication.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedAuthentication.deleteAll()
    }
}
