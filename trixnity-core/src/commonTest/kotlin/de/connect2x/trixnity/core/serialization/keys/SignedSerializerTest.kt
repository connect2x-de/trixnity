package de.connect2x.trixnity.core.serialization.keys

import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.DeviceKeys
import de.connect2x.trixnity.core.model.keys.EncryptionAlgorithm
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.core.model.keys.Key.Ed25519Key
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.Keys
import de.connect2x.trixnity.core.model.keys.Signed
import de.connect2x.trixnity.core.model.keys.SignedDeviceKeys
import de.connect2x.trixnity.core.model.keys.keysOf
import de.connect2x.trixnity.core.serialization.trimToFlatJson
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SignedSerializerTest : TrixnityBaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    private val signedObjectWithoutRaw =
        Signed(
            signed =
                DeviceKeys(
                    userId = UserId("@user1:localhost:8008"),
                    deviceId = "ROPJIGSSUZ",
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys =
                        Keys(
                            keys =
                                keysOf(
                                    Key.Curve25519Key(
                                        id = "ROPJIGSSUZ",
                                        value =
                                            KeyValue.Curve25519KeyValue(
                                                value = "0PV38Obewq8s9DJyc13HLt0brMr0+Y1btX3QQ4qriCw"
                                            ),
                                    ),
                                    Key.Ed25519Key(
                                        id = "ROPJIGSSUZ",
                                        value =
                                            KeyValue.Ed25519KeyValue(
                                                value = "0sIDd8WpPKAj5JdsriYZbfeKL6oBhnIJqcJHZyMsoqI"
                                            ),
                                    ),
                                )
                        ),
                    dehydrated = null,
                ),
            signedRaw = null,
            signatures =
                mapOf(
                    UserId("@user1:localhost:8008") to
                        keysOf(
                            Key.Ed25519Key(
                                id = "ROPJIGSSUZ",
                                value =
                                    KeyValue.Ed25519KeyValue(
                                        value =
                                            "/4m9g6KnP4RqLH8tTtzDnI2cqqSXQx6T7UGqQAqhARlPmCm9gK2ce94HNPFgbkqHYnM23e++RVdjI7C2hwr0Bw"
                                    ),
                            )
                        )
                ),
        )
    private val signedObjectWithoutRawSerialized =
        """
        {
            "user_id": "@user1:localhost:8008",
            "device_id": "ROPJIGSSUZ",
            "algorithms": [
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
            ],
            "keys": {
                "curve25519:ROPJIGSSUZ": "0PV38Obewq8s9DJyc13HLt0brMr0+Y1btX3QQ4qriCw",
                "ed25519:ROPJIGSSUZ": "0sIDd8WpPKAj5JdsriYZbfeKL6oBhnIJqcJHZyMsoqI"
            },
            "signatures": {
                "@user1:localhost:8008": {
                    "ed25519:ROPJIGSSUZ": "/4m9g6KnP4RqLH8tTtzDnI2cqqSXQx6T7UGqQAqhARlPmCm9gK2ce94HNPFgbkqHYnM23e++RVdjI7C2hwr0Bw"
                }
            }
        }
        """
            .trimToFlatJson()
    private val signedObjectWithRaw =
        Signed(
            signed =
                DeviceKeys(
                    userId = UserId("@user1:localhost:8008"),
                    deviceId = "ROPJIGSSUZ",
                    algorithms = setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keys =
                        Keys(
                            keys =
                                keysOf(
                                    Key.Curve25519Key(
                                        id = "ROPJIGSSUZ",
                                        value =
                                            KeyValue.Curve25519KeyValue(
                                                value = "0PV38Obewq8s9DJyc13HLt0brMr0+Y1btX3QQ4qriCw"
                                            ),
                                    ),
                                    Key.Ed25519Key(
                                        id = "ROPJIGSSUZ",
                                        value =
                                            KeyValue.Ed25519KeyValue(
                                                value = "0sIDd8WpPKAj5JdsriYZbfeKL6oBhnIJqcJHZyMsoqI"
                                            ),
                                    ),
                                )
                        ),
                    dehydrated = null,
                ),
            signedRaw =
                buildJsonObject {
                    put("user_id", "@user1:localhost:8008")
                    put("device_id", "ROPJIGSSUZ")
                    put(
                        "algorithms",
                        buildJsonArray {
                            add("m.olm.v1.curve25519-aes-sha2")
                            add("m.megolm.v1.aes-sha2")
                        },
                    )
                    put(
                        "keys",
                        buildJsonObject {
                            put("curve25519:ROPJIGSSUZ", "0PV38Obewq8s9DJyc13HLt0brMr0+Y1btX3QQ4qriCw")
                            put("ed25519:ROPJIGSSUZ", "0sIDd8WpPKAj5JdsriYZbfeKL6oBhnIJqcJHZyMsoqI")
                        },
                    )
                    put("unsigned", buildJsonObject {})
                },
            signatures =
                mapOf(
                    UserId("@user1:localhost:8008") to
                        keysOf(
                            Key.Ed25519Key(
                                id = "ROPJIGSSUZ",
                                value =
                                    KeyValue.Ed25519KeyValue(
                                        value =
                                            "/4m9g6KnP4RqLH8tTtzDnI2cqqSXQx6T7UGqQAqhARlPmCm9gK2ce94HNPFgbkqHYnM23e++RVdjI7C2hwr0Bw"
                                    ),
                            )
                        )
                ),
        )
    private val signedObjectWithRawSerialized =
        """
        {
            "user_id": "@user1:localhost:8008",
            "device_id": "ROPJIGSSUZ",
            "algorithms": [
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
            ],
            "keys": {
                "curve25519:ROPJIGSSUZ": "0PV38Obewq8s9DJyc13HLt0brMr0+Y1btX3QQ4qriCw",
                "ed25519:ROPJIGSSUZ": "0sIDd8WpPKAj5JdsriYZbfeKL6oBhnIJqcJHZyMsoqI"
            },
            "unsigned": {},
            "signatures": {
                "@user1:localhost:8008": {
                    "ed25519:ROPJIGSSUZ": "/4m9g6KnP4RqLH8tTtzDnI2cqqSXQx6T7UGqQAqhARlPmCm9gK2ce94HNPFgbkqHYnM23e++RVdjI7C2hwr0Bw"
                }
            }
        }
    """
            .trimToFlatJson()

    @Test
    fun shouldSerializeSignedWithRaw() {
        json.encodeToString(signedObjectWithRaw) shouldBe signedObjectWithRawSerialized
    }

    @Test
    fun shouldDeserializeSignedWithRaw() {
        json.decodeFromString<SignedDeviceKeys>(signedObjectWithRawSerialized) shouldBe signedObjectWithRaw
    }

    @Test
    fun shouldSerializeSignedWithoutRaw() {
        json.encodeToString(signedObjectWithoutRaw) shouldBe signedObjectWithoutRawSerialized
    }

    @Test
    fun shouldDeserializeSignedWithoutRaw() {
        json.decodeFromString<SignedDeviceKeys>(signedObjectWithoutRawSerialized) shouldBe signedObjectWithoutRaw
    }

    @Test
    fun shouldSerializeSignaturesOfUserIds() {
        val content =
            Signed(
                DeviceKeys(
                    UserId("alice", "example.com"),
                    "ALICEDEVICE",
                    setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                    keysOf(Ed25519Key("ABC", "keyValue"), Key.Curve25519Key("DEF", "keyValue")),
                ),
                mapOf(UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey"))),
            )

        val expectedResult =
            """
            {
              "user_id":"@alice:example.com",
              "device_id":"ALICEDEVICE",
              "algorithms":[
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
              ],
              "keys":{
                "ed25519:ABC":"keyValue",
                "curve25519:DEF":"keyValue"
              },
              "signatures":{
                "@alice:example.com":{
                  "ed25519:JLAFKJWSCS":"aKey"
                }
              }
            }
            """
                .trimIndent()
                .lines()
                .joinToString("") { it.trim() }
        val result = json.encodeToString(content)
        assertEquals(expectedResult, result)
    }

    @Test
    fun shouldDeserializeSignaturesOfUserIds() {
        val input =
            """
            {
              "user_id": "@alice:example.com",
              "device_id": "ALICEDEVICE",
              "algorithms": [
                "m.olm.v1.curve25519-aes-sha2",
                "m.megolm.v1.aes-sha2"
              ],
              "keys": {
                "ed25519:ABC": "keyValue",
                "curve25519:DEF": "keyValue"
              },
              "signatures": {
                "@alice:example.com": {
                  "ed25519:JLAFKJWSCS": "aKey"
                }
              }
            }
            """
                .trimIndent()
        val result = json.decodeFromString<Signed<DeviceKeys, UserId>>(input)
        assertEquals(
            DeviceKeys(
                UserId("alice", "example.com"),
                "ALICEDEVICE",
                setOf(EncryptionAlgorithm.Olm, EncryptionAlgorithm.Megolm),
                keysOf(Ed25519Key("ABC", "keyValue"), Key.Curve25519Key("DEF", "keyValue")),
            ),
            result.signed,
        )
        assertEquals(
            mapOf(UserId("alice", "example.com") to keysOf(Ed25519Key("JLAFKJWSCS", "aKey"))),
            result.signatures,
        )
    }
}
