package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OlmForgetFallbackKeyAfterRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlin.time.Instant
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedOlmForgetFallbackKeyAfter : LongIdTable("olm_forget_fallback_key_after") {
    val value = long("value")
}

internal class ExposedOlmForgetFallbackKeyAfterRepository : OlmForgetFallbackKeyAfterRepository {
    context(transaction: ReadTransaction)
    override suspend operator fun get(key: Long): Instant? {
        return ExposedOlmForgetFallbackKeyAfter.selectAll()
            .where { ExposedOlmForgetFallbackKeyAfter.id eq key }
            .firstOrNull()
            ?.let { it[ExposedOlmForgetFallbackKeyAfter.value] }
            ?.let { Instant.fromEpochMilliseconds(it) }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Instant) {
        ExposedOlmForgetFallbackKeyAfter.upsert {
            it[ExposedOlmForgetFallbackKeyAfter.id] = key
            it[ExposedOlmForgetFallbackKeyAfter.value] = value.toEpochMilliseconds()
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedOlmForgetFallbackKeyAfter.deleteWhere { ExposedOlmForgetFallbackKeyAfter.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedOlmForgetFallbackKeyAfter.deleteAll()
    }
}
