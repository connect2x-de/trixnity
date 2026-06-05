package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.admin.GetLock
import de.connect2x.trixnity.clientserverapi.model.admin.GetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.SetLock
import de.connect2x.trixnity.clientserverapi.model.admin.SetSuspend
import de.connect2x.trixnity.clientserverapi.model.admin.WhoIs

interface AdminApiHandler {
    /**
     * @see [GetSuspend]
     */
    suspend fun getSuspend(content: MatrixEndpointContext<GetSuspend, Unit, GetSuspend.Response>): GetSuspend.Response

    /**
     * @see [SetSuspend]
     */
    suspend fun setSuspend(content: MatrixEndpointContext<SetSuspend, SetSuspend.Request, SetSuspend.Response>): SetSuspend.Response

    /**
     * @see [GetLock]
     */
    suspend fun getLock(content: MatrixEndpointContext<GetLock, Unit, GetLock.Response>): GetLock.Response

    /**
     * @see [SetLock]
     */
    suspend fun setLock(content: MatrixEndpointContext<SetLock, SetLock.Request, SetLock.Response>): SetLock.Response

    /**
     * @see [WhoIs]
     */
    suspend fun whoIs(content: MatrixEndpointContext<WhoIs, Unit, WhoIs.Response>): WhoIs.Response
}
