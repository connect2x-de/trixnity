package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.admin.GetLock
import de.connect2x.trixnity.clientserverapi.model.admin.GetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.SetLock
import de.connect2x.trixnity.clientserverapi.model.admin.SetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.WhoIs
import de.connect2x.trixnity.core.model.UserId

interface AdminApiClient {
    /**
     * @see [GetSuspend]
     */
    suspend fun getSuspend(userId: UserId): Result<GetSuspend.Response>

    /**
     * @see [SetSuspend]
     */
    suspend fun setSuspend(userId: UserId, suspended: Boolean): Result<SetSuspend.Response>

    /**
     * @see [GetLock]
     */
    suspend fun getLock(userId: UserId): Result<GetLock.Response>

    /**
     * @see [SetLock]
     */
    suspend fun setLock(userId: UserId, locked: Boolean): Result<SetLock.Response>

    /**
     * @see [WhoIs]
     */
    suspend fun whoIs(userId: UserId): Result<WhoIs.Response>
}

class AdminApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : AdminApiClient {
    override suspend fun getSuspend(userId: UserId): Result<GetSuspend.Response> =
        baseClient.request(GetSuspend(userId))

    override suspend fun setSuspend(userId: UserId, suspended: Boolean): Result<SetSuspend.Response> =
        baseClient.request(SetSuspend(userId), SetSuspend.Request(suspended))

    override suspend fun getLock(userId: UserId): Result<GetLock.Response> =
        baseClient.request(GetLock(userId))

    override suspend fun setLock(userId: UserId, locked: Boolean): Result<SetLock.Response> =
        baseClient.request(SetLock(userId), SetLock.Request(locked))

    override suspend fun whoIs(userId: UserId): Result<WhoIs.Response> =
        baseClient.request(WhoIs(userId))
}
