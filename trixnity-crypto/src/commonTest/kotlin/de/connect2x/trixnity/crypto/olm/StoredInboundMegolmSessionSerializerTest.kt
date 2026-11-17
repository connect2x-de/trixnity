package de.connect2x.trixnity.crypto.olm

import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.KeyValue
import de.connect2x.trixnity.core.model.keys.RoomKeyWithheldCode
import de.connect2x.trixnity.core.serialization.createMatrixEventJson
import de.connect2x.trixnity.crypto.trimToFlatJson
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class StoredInboundMegolmSessionSerializerTest {
    private val json = createMatrixEventJson()

    val storedInboundMegolmSessionCreator: StoredInboundMegolmSession =
        StoredInboundMegolmSession.Key(
            roomId = RoomId("roomId"),
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            source = InboundMegolmSessionSource.Creator,
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionCreatorJson =
        """
            {
              "type": "key",
              "roomId": "roomId",
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "source": {
                "type": "creator"
              },
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val legacySourceStoredInboundMegolmSessionCreatorJson =
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

    val storedInboundMegolmSessionBackup: StoredInboundMegolmSession =
        StoredInboundMegolmSession.Key(
            roomId = RoomId("roomId"),
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            source = InboundMegolmSessionSource.UnauthenticatedBackup(null),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionBackupJson =
        """
            {
              "type": "key",
              "roomId": "roomId",
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "source": {
                "type": "backup"
              },
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
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

    val storedInboundMegolmSessionKeyRequest: StoredInboundMegolmSession =
        StoredInboundMegolmSession.Key(
            roomId = RoomId("roomId"),
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            source = InboundMegolmSessionSource.KeyRequest(listOf(KeyValue.Curve25519KeyValue("key1"))),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionKeyRequestJson =
        """
            {
              "type": "key",
              "roomId": "roomId",
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "source": {
                "type": "keyRequest",
                "forwardingKeyChain": [
                  "key1"
                ]
              },
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
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

    val storedInboundMegolmSessionKeyBundle: StoredInboundMegolmSession =
        StoredInboundMegolmSession.Key(
            roomId = RoomId("roomId"),
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            senderSigningKey = KeyValue.Ed25519KeyValue("senderSigningKey"),
            sessionId = "sessionId",
            source =
                InboundMegolmSessionSource.KeyBundle(
                    setOf(InboundMegolmSessionSource.KeyBundle.Sender(UserId("alice", "server"), "device"))
                ),
            firstKnownIndex = 12,
            hasBeenBackedUp = false,
            sharedHistory = false,
            pickled = "pickled",
        )

    val storedInboundMegolmSessionKeyBundleJson =
        """
            {
              "type": "key",
              "roomId": "roomId",
              "senderKey": "senderKey",
              "senderSigningKey": "senderSigningKey",
              "sessionId": "sessionId",
              "source": {
                "type": "keyBundle",
                "sender": [{"userId":"@alice:server","deviceId":"device"}]
              },
              "firstKnownIndex": 12,
              "hasBeenBackedUp": false,
              "sharedHistory": false,
              "pickled": "pickled"
            }
        """
            .trimToFlatJson()

    val storedInboundMegolmSessionWithheld: StoredInboundMegolmSession =
        StoredInboundMegolmSession.Withheld(
            roomId = RoomId("roomId"),
            senderKey = KeyValue.Curve25519KeyValue("senderKey"),
            sessionId = "sessionId",
            source = InboundMegolmSessionSource.Creator,
            code = RoomKeyWithheldCode.Blacklisted,
            reason = "withheld reason",
        )

    val storedInboundMegolmSessionWithheldJson =
        """
            {
              "type": "withheld",
              "roomId": "roomId",
              "senderKey": "senderKey",
              "sessionId": "sessionId",
              "source": {
                "type": "creator"
              },
              "code": "m.blacklisted",
              "reason": "withheld reason"
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
    fun shouldDeserializeLegacySourceStoredInboundMegolmSessionCreator() {
        val result =
            json.decodeFromString<StoredInboundMegolmSession>(legacySourceStoredInboundMegolmSessionCreatorJson)
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

    @Test
    fun shouldSerializeStoredInboundMegolmSessionWithheld() {
        val result = json.encodeToString(storedInboundMegolmSessionWithheld)
        result shouldBe storedInboundMegolmSessionWithheldJson
    }

    @Test
    fun shouldDeserializeStoredInboundMegolmSessionWithheld() {
        val result = json.decodeFromString<StoredInboundMegolmSession>(storedInboundMegolmSessionWithheldJson)
        result shouldBe storedInboundMegolmSessionWithheld
    }
}
