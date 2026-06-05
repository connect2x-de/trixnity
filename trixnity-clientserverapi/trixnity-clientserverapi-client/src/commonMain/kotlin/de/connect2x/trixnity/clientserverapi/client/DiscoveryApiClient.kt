package de.connect2x.trixnity.clientserverapi.client

import de.connect2x.trixnity.clientserverapi.model.discovery.DiscoveryInformation
import de.connect2x.trixnity.clientserverapi.model.discovery.GetClient
import de.connect2x.trixnity.clientserverapi.model.discovery.GetPolicyServer
import de.connect2x.trixnity.clientserverapi.model.discovery.GetSupport

interface DiscoveryApiClient {
    /**
     * @see [GetClient]
     */
    @Deprecated("use getClient() instead.", ReplaceWith("getClient()"))
    suspend fun getWellKnown(): Result<DiscoveryInformation>

    /**
     * @see [GetClient]
     */
    suspend fun getClient(): Result<DiscoveryInformation>

    /**
     * @see [GetSupport]
     */
    suspend fun getSupport(): Result<GetSupport.Response>

    /**
     * @see [GetPolicyServer]
     */
    suspend fun getPolicyServer(): Result<GetPolicyServer.Response>
}

class DiscoveryApiClientImpl(
    private val baseClient: MatrixClientServerApiBaseClient
) : DiscoveryApiClient {

    @Deprecated("use getClient() instead.", replaceWith = ReplaceWith("getClient()"))
    override suspend fun getWellKnown(): Result<DiscoveryInformation> = getClient()

    override suspend fun getClient(): Result<DiscoveryInformation> =
        baseClient.request(GetClient)

    override suspend fun getSupport(): Result<GetSupport.Response> =
        baseClient.request(GetSupport)

    override suspend fun getPolicyServer(): Result<GetPolicyServer.Response> =
        baseClient.request(GetPolicyServer)

}
