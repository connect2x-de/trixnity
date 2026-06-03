package de.connect2x.trixnity.core.model.keys

import de.connect2x.trixnity.core.model.keys.Key.Curve25519Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.Key.SignedCurve25519Key
import de.connect2x.trixnity.core.model.keys.Key.UnknownKey
import de.connect2x.trixnity.core.model.keys.KeyValue.Curve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.Ed25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.SignedCurve25519KeyValue
import de.connect2x.trixnity.core.model.keys.KeyValue.UnknownKeyValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.jvm.JvmInline

@Serializable(with = Keys.Serializer::class)
@JvmInline
value class Keys(
    val keys: Set<Key>
) : Set<Key> by keys {
    constructor(vararg keys: Key) : this(keys.toSet())

    object Serializer : KSerializer<Keys> {
        override fun deserialize(decoder: Decoder): Keys {
            require(decoder is JsonDecoder)
            val jsonObj = decoder.decodeJsonElement().jsonObject
            return Keys(
                jsonObj.map { (key, value) ->
                    val algorithm = KeyAlgorithm.of(key.substringBefore(":"))
                    val keyId = key.substringAfter(":", "")
                        .let { foundKeyId -> foundKeyId.ifEmpty { null } }
                    when (algorithm) {
                        KeyAlgorithm.Ed25519 ->
                            Ed25519Key(keyId, decoder.json.decodeFromJsonElement<Ed25519KeyValue>(value))

                        KeyAlgorithm.Curve25519 ->
                            Curve25519Key(keyId, decoder.json.decodeFromJsonElement<Curve25519KeyValue>(value))

                        KeyAlgorithm.SignedCurve25519 ->
                            SignedCurve25519Key(
                                keyId,
                                decoder.json.decodeFromJsonElement<SignedCurve25519KeyValue>(value)
                            )

                        is KeyAlgorithm.Unknown ->
                            UnknownKey(
                                id = keyId,
                                value = UnknownKeyValue(value),
                                algorithm = KeyAlgorithm.Unknown(algorithm.name)
                            )
                    }
                }.toSet()
            )
        }

        override fun serialize(encoder: Encoder, value: Keys) {
            require(encoder is JsonEncoder)
            encoder.encodeJsonElement(
                JsonObject(value.keys.associate { key ->
                    when (key) {
                        is Ed25519Key -> key.fullId to encoder.json.encodeToJsonElement(key.value)
                        is Curve25519Key -> key.fullId to encoder.json.encodeToJsonElement(key.value)
                        is SignedCurve25519Key -> key.fullId to encoder.json.encodeToJsonElement(key.value)
                        is UnknownKey -> key.fullId to encoder.json.encodeToJsonElement(key.value)
                    }
                })
            )
        }

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Keys")
    }
}

fun keysOf(vararg keys: Key) = Keys(keys.toSet())
