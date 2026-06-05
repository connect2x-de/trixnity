package de.connect2x.trixnity.serverserverapi.model.policy

import de.connect2x.trixnity.core.HttpMethod
import de.connect2x.trixnity.core.HttpMethodType.POST
import de.connect2x.trixnity.core.MatrixEndpoint
import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signatures
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import de.connect2x.trixnity.serverserverapi.model.SignedPersistentDataUnit
import io.ktor.resources.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * @see <a href="https://spec.matrix.org/v1.18/server-server-api/#post_matrixpolicyv1sign">matrix spec</a>
 */
@Serializable
@Resource("/_matrix/policy/v1/sign")
@HttpMethod(POST)
object Sign : MatrixEndpoint<SignedPersistentDataUnit<*>, Signatures<String>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun requestSerializerBuilder(
        mappings: EventContentSerializerMappings,
        json: Json,
        value: SignedPersistentDataUnit<*>?
    ): KSerializer<SignedPersistentDataUnit<*>> {
        @Suppress("UNCHECKED_CAST")
        val serializer = requireNotNull(json.serializersModule.getContextual(PersistentDataUnit::class))
        return Signed.serializer(serializer, String.serializer())
    }
}
