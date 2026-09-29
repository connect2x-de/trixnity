package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.trimToFlatJson
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StoredInboundMegolmSessionSerializerTest {
    private val json = createMatrixEventJson()

    val storedInboundMegolmSessionCreator =
        StoredInboundMegolmSession(
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            roomId = RoomId("roomId"),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            source = InboundMegolmSessionSource.Creator,
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionCreatorJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "source": {
                "type": "creator"
              },
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val legacyStoredInboundMegolmSessionCreatorJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "isTrusted": true,
              "forwardingCurve25519KeyChain": [],
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val storedInboundMegolmSessionBackup =
        StoredInboundMegolmSession(
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            roomId = RoomId("roomId"),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            source = InboundMegolmSessionSource.UnauthenticatedBackup(null),
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionBackupJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "source": {
                "type": "backup"
              },
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val legacyStoredInboundMegolmSessionBackupJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "isTrusted": false,
              "forwardingCurve25519KeyChain": [],
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val storedInboundMegolmSessionKeyRequest =
        StoredInboundMegolmSession(
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            roomId = RoomId("roomId"),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            source = InboundMegolmSessionSource.KeyRequest(listOf(KeyValue.Curve25519KeyValue("key1"))),
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionKeyRequestJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "source": {
                "type": "keyRequest",
                "forwardingKeyChain": [
                  "key1"
                ]
              },
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val legacyStoredInboundMegolmSessionKeyRequestJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "isTrusted": false,
              "forwardingCurve25519KeyChain": ["key1"],
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val storedInboundMegolmSessionKeyBundle =
        StoredInboundMegolmSession(
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            roomId = RoomId("roomId"),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            source =
                InboundMegolmSessionSource.KeyBundle(
                    setOf(InboundMegolmSessionSource.KeyBundle.Sender(UserId("alice", "server"), "device"))
                ),
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionKeyBundleJson =
        """
            {
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "roomId": "roomId",
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "source": {
                "type": "keyBundle",
                "sender": [{"userId":"@alice:server","deviceId":"device"}]
              },
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    @Test
    fun shouldSerializeStoredInboundMegolmSessionCreator() {
        val result = json.encodeToString(storedInboundMegolmSessionCreator)
        result shouldBe storedInboundMegolmSessionCreatorJson
    }

    @Test
    fun shouldDeserializeStoredInboundMegolmSessionCreator() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(storedInboundMegolmSessionCreatorJson)
        result shouldBe storedInboundMegolmSessionCreator
    }

    @Test
    fun shouldDeserializeLegacyStoredInboundMegolmSessionCreator() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(legacyStoredInboundMegolmSessionCreatorJson)
        result shouldBe storedInboundMegolmSessionCreator
    }

    @Test
    fun shouldSerializeStoredInboundMegolmSessionBackup() {
        val result = json.encodeToString(storedInboundMegolmSessionBackup)
        result shouldBe storedInboundMegolmSessionBackupJson
    }

    @Test
    fun shouldDeserializeStoredInboundMegolmSessionBackup() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(storedInboundMegolmSessionBackupJson)
        result shouldBe storedInboundMegolmSessionBackup
    }

    @Test
    fun shouldDeserializeLegacyStoredInboundMegolmSessionBackup() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(legacyStoredInboundMegolmSessionBackupJson)
        result shouldBe storedInboundMegolmSessionBackup
    }

    @Test
    fun shouldSerializeStoredInboundMegolmSessionKeyRequest() {
        val result = json.encodeToString(storedInboundMegolmSessionKeyRequest)
        result shouldBe storedInboundMegolmSessionKeyRequestJson
    }

    @Test
    fun shouldDeserializeStoredInboundMegolmSessionKeyRequest() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(storedInboundMegolmSessionKeyRequestJson)
        result shouldBe storedInboundMegolmSessionKeyRequest
    }

    @Test
    fun shouldDeserializeLegacyStoredInboundMegolmSessionKeyRequest() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(legacyStoredInboundMegolmSessionKeyRequestJson)
        result shouldBe storedInboundMegolmSessionKeyRequest
    }

    @Test
    fun shouldSerializeStoredInboundMegolmSessionKeyBundle() {
        val result = json.encodeToString(storedInboundMegolmSessionKeyBundle)
        result shouldBe storedInboundMegolmSessionKeyBundleJson
    }

    @Test
    fun shouldDeserializeStoredInboundMegolmSessionKeyBundle() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(storedInboundMegolmSessionKeyBundleJson)
        result shouldBe storedInboundMegolmSessionKeyBundle
    }
}
