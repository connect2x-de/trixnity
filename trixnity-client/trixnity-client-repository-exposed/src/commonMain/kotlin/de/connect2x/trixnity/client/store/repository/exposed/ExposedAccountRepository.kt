package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.store.Account
import de.connect2x.trixnity.client.store.repository.AccountRepository
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

internal object ExposedAccount : LongIdTable("account") {
    val olmPickleKey = text("olm_pickle_key").nullable()
    val baseUrl = text("base_url").nullable()
    val userId = text("user_id").nullable()
    val deviceId = text("device_id").nullable()
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val syncBatchToken = text("sync_batch_token").nullable()
    val filter = text("filter").nullable()
    val profile = text("profile").nullable()
}

internal class ExposedAccountRepository(private val json: Json) : AccountRepository {
    context(transaction: ReadTransaction)
    override suspend fun get(key: Long): Account? {
        return ExposedAccount.selectAll().where { ExposedAccount.id eq key }
            .firstOrNull()
            ?.let {
                Account(
                    olmPickleKey = it[ExposedAccount.olmPickleKey],
                    baseUrl = it[ExposedAccount.baseUrl],
                    userId = it[ExposedAccount.userId]?.let { it1 -> UserId(it1) }
                        ?: throw IllegalStateException("userId not found"),
                    deviceId = it[ExposedAccount.deviceId] ?: throw IllegalStateException("deviceId not found"),
                    accessToken = it[ExposedAccount.accessToken],
                    refreshToken = it[ExposedAccount.refreshToken],
                    syncBatchToken = it[ExposedAccount.syncBatchToken],
                    filter = it[ExposedAccount.filter]?.let { json.decodeFromString(it) },
                    profile = it[ExposedAccount.profile]?.let { json.decodeFromString(it) },
                )
            }
    }

    context(transaction: WriteTransaction)
    override suspend fun save(key: Long, value: Account) {
        @Suppress("DEPRECATION")
        ExposedAccount.upsert {
            it[id] = key
            it[olmPickleKey] = value.olmPickleKey
            it[baseUrl] = value.baseUrl
            it[userId] = value.userId.full
            it[deviceId] = value.deviceId
            it[accessToken] = value.accessToken
            it[refreshToken] = value.refreshToken
            it[syncBatchToken] = value.syncBatchToken
            it[filter] = value.filter?.let { json.encodeToString(it) }
            it[profile] = value.profile?.let { json.encodeToString(it) }
        }
    }

    context(transaction: WriteTransaction)
    override suspend fun delete(key: Long) {
        ExposedAccount.deleteWhere { ExposedAccount.id eq key }
    }

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() {
        ExposedAccount.deleteAll()
    }
}

