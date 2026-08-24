package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.OlmAccountRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedOlmAccount : LongIdTable("olm_account") {
    val pickled = text("pickled")
}

internal class ExposedOlmAccountRepository : OlmAccountRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): String? {
        return ExposedOlmAccount.selectAll()
            .where { ExposedOlmAccount.id eq key }
            .firstOrNull()
            ?.let { it[ExposedOlmAccount.pickled] }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: String) {
        ExposedOlmAccount.upsert {
            it[ExposedOlmAccount.id] = key
            it[pickled] = value
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedOlmAccount.deleteWhere { ExposedOlmAccount.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedOlmAccount.deleteAll()
    }
}
