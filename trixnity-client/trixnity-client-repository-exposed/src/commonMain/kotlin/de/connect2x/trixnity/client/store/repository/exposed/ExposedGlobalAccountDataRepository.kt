package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.GlobalAccountDataRepository
import de.connect2x.trixnity.core.model.events.ClientEvent.GlobalAccountDataEvent
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.associate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedGlobalAccountData : Table("global_account_data") {
    val type = varchar("type", length = 255)
    val key = varchar("key", length = 255)
    override val primaryKey = PrimaryKey(type, key)
    val event = text("event")
}

internal class ExposedGlobalAccountDataRepository(private val json: Json) : GlobalAccountDataRepository {
    @OptIn(ExperimentalSerializationApi::class)
    private val serializer =
        json.serializersModule.getContextual(GlobalAccountDataEvent::class)
            ?: throw IllegalArgumentException("could not find event serializer")

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: String): Map<String, GlobalAccountDataEvent<*>> {
        return ExposedGlobalAccountData.selectAll()
            .where { ExposedGlobalAccountData.type.eq(firstKey) }
            .associate {
                it[ExposedGlobalAccountData.key] to
                    json.decodeFromString(serializer, it[ExposedGlobalAccountData.event])
            }
    }

    context(transaction: ReadTransaction)
    override suspend fun get(firstKey: String, secondKey: String): GlobalAccountDataEvent<*>? =
        ExposedGlobalAccountData.selectAll()
            .where { ExposedGlobalAccountData.type.eq(firstKey) and ExposedGlobalAccountData.key.eq(secondKey) }
            .firstOrNull()
            ?.let { json.decodeFromString(serializer, it[ExposedGlobalAccountData.event]) }

    context(transaction: WriteTransaction)
    override suspend fun save(firstKey: String, secondKey: String, value: GlobalAccountDataEvent<*>) {
        ExposedGlobalAccountData.upsert {
            it[type] = firstKey
            it[key] = secondKey
            it[event] = json.encodeToString(serializer, value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(firstKey: String, secondKey: String) {
        ExposedGlobalAccountData.deleteWhere { type.eq(firstKey) and key.eq(secondKey) }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedGlobalAccountData.deleteAll()
    }
}
