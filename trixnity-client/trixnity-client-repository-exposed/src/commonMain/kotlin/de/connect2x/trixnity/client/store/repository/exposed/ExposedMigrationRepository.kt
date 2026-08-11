package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.repository.MigrationRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedMigration : Table("migration") {
    val name = text("name")
    val metadata = text("metadata")

    override val primaryKey: PrimaryKey = PrimaryKey(name)
}

internal class ExposedMigrationRepository : MigrationRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: String): String? {
        return ExposedMigration
            .selectAll()
            .where { ExposedMigration.name eq key }
            .firstOrNull()
            ?.get(ExposedMigration.metadata)
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: String) {
        ExposedMigration.upsert {
            it[name] = key
            it[metadata] = value
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) {
        ExposedMigration.deleteWhere { ExposedMigration.name eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedMigration.deleteAll()
    }
}

