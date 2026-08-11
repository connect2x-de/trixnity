package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.StoredDeviceKeys
import de.connect2x.trixnity.client.store.repository.DeviceKeysRepository
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.upsert

internal object ExposedDeviceKeys : Table("device_keys") {
    val userId = varchar("user_id", length = 255)
    override val primaryKey = PrimaryKey(userId)
    val value = text("value")
}

internal class ExposedDeviceKeysRepository(private val json: Json) : DeviceKeysRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: UserId): Map<String, StoredDeviceKeys>? {
        return ExposedDeviceKeys.selectAll().where { ExposedDeviceKeys.userId eq key.full }.firstOrNull()?.let {
            it[ExposedDeviceKeys.value].let { deviceKeys ->
                json.decodeFromString<Map<String, StoredDeviceKeys>>(deviceKeys)
            }
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: UserId, value: Map<String, StoredDeviceKeys>) {
        ExposedDeviceKeys.upsert {
            it[userId] = key.full
            it[ExposedDeviceKeys.value] = json.encodeToString(value)
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: UserId) {
        ExposedDeviceKeys.deleteWhere { userId eq key.full }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedDeviceKeys.deleteAll()
    }
}
