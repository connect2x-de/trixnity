package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = StoredInboundMegolmSession.Serializer::class)
@KeepGeneratedSerializer
data class StoredInboundMegolmSession(
    val senderKey: Curve25519KeyValue,
    val senderSigningKey: Ed25519KeyValue,
    val sessionId: String,
    val roomId: RoomId,
    val firstKnownIndex: Long,
    val hasBeenBackedUp: Boolean,
    val source: InboundMegolmSessionSource,
    val sharedHistory: Boolean,
    val pickled: String,
) {

    @BackwardsCompatible
    constructor(
        senderKey: Curve25519KeyValue,
        senderSigningKey: Ed25519KeyValue,
        sessionId: String,
        roomId: RoomId,
        firstKnownIndex: Long,
        hasBeenBackedUp: Boolean,
        isTrusted: Boolean,
        forwardingCurve25519KeyChain: List<Curve25519KeyValue>,
        pickled: String,
    ) : this(
        senderKey = senderKey,
        senderSigningKey = senderSigningKey,
        sessionId = sessionId,
        roomId = roomId,
        firstKnownIndex = firstKnownIndex,
        hasBeenBackedUp = hasBeenBackedUp,
        source =
            when {
                forwardingCurve25519KeyChain.isNotEmpty() ->
                    // best guess
                    InboundMegolmSessionSource.KeyRequest(forwardingCurve25519KeyChain)
                isTrusted -> InboundMegolmSessionSource.Creator
                else -> InboundMegolmSessionSource.UnauthenticatedBackup()
            },
        sharedHistory = false, // information lies in the past
        pickled = pickled,
    )

    /** use for backwards compatibility only */
    @RequiresOptIn(message = "This API is experimental. It could change in the future without notice.")
    @Retention(AnnotationRetention.BINARY)
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.FIELD,
        AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.TYPEALIAS,
    )
    annotation class BackwardsCompatible

    @Serializable
    private data class Legacy(
        val senderKey: Curve25519KeyValue,
        val senderSigningKey: Ed25519KeyValue,
        val sessionId: String,
        val roomId: RoomId,
        val firstKnownIndex: Long,
        val hasBeenBackedUp: Boolean,
        /**
         * This means, that we can trust the communication channel from which we received the session from. For example
         * the key backup cannot be trusted due to async encryption. This does NOT mean, that we trust this megolm
         * session. It needs to be checked whether we trust the sender key.
         */
        val isTrusted: Boolean,
        val forwardingCurve25519KeyChain: List<Curve25519KeyValue>,
        val pickled: String,
    )

    object Serializer : KSerializer<StoredInboundMegolmSession> {
        override val descriptor = buildClassSerialDescriptor("StoredInboundMegolmSession")

        override fun deserialize(decoder: Decoder): StoredInboundMegolmSession {
            require(decoder is JsonDecoder)
            val jsonObject = decoder.decodeJsonElement().jsonObject
            return if (jsonObject.containsKey("source")) {
                decoder.json.decodeFromJsonElement(StoredInboundMegolmSession.generatedSerializer(), jsonObject)
            } else {
                val legacy = decoder.json.decodeFromJsonElement(Legacy.serializer(), jsonObject)
                @OptIn(BackwardsCompatible::class)
                StoredInboundMegolmSession(
                    senderKey = legacy.senderKey,
                    senderSigningKey = legacy.senderSigningKey,
                    sessionId = legacy.sessionId,
                    roomId = legacy.roomId,
                    firstKnownIndex = legacy.firstKnownIndex,
                    isTrusted = legacy.isTrusted,
                    hasBeenBackedUp = legacy.hasBeenBackedUp,
                    forwardingCurve25519KeyChain = legacy.forwardingCurve25519KeyChain,
                    pickled = legacy.pickled,
                )
            }
        }

        override fun serialize(encoder: Encoder, value: StoredInboundMegolmSession) {
            encoder.encodeSerializableValue(StoredInboundMegolmSession.generatedSerializer(), value)
        }
    }
}
