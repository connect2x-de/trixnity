package de.connect2x.trixnity.clientserverapi.server

import de.connect2x.trixnity.api.server.MatrixEndpointContext
import de.connect2x.trixnity.clientserverapi.model.discovery.DiscoveryInformation
import de.connect2x.trixnity.clientserverapi.model.discovery.GetClient
import de.connect2x.trixnity.clientserverapi.model.discovery.GetPolicyServer
import de.connect2x.trixnity.clientserverapi.model.discovery.GetSupport

interface DiscoveryApiHandler {

    /**
     * @see [GetClient]
     */
    suspend fun getClient(context: MatrixEndpointContext<GetClient, Unit, DiscoveryInformation>): DiscoveryInformation

    /**
     * @see [GetSupport]
     */
    suspend fun getSupport(context: MatrixEndpointContext<GetSupport, Unit, GetSupport.Response>): GetSupport.Response

    /**
     * @see [GetPolicyServer]
     */
    suspend fun getPolicyServer(context: MatrixEndpointContext<GetPolicyServer, Unit, GetPolicyServer.Response>): GetPolicyServer.Response
}
