package de.connect2x.trixnity.client.verification

import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.warn
import de.connect2x.trixnity.client.key.KeyTrustService
import de.connect2x.trixnity.client.store.KeyStore
import de.connect2x.trixnity.clientserverapi.client.MatrixClientServerApiClient
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.ClientEvent
import de.connect2x.trixnity.core.model.events.ClientEvent.ToDeviceEvent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Accepted
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent.Code.Timeout
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationMethod
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationReadyEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationRequestToDeviceEventContent
import de.connect2x.trixnity.core.model.events.m.key.verification.VerificationStep
import de.connect2x.trixnity.core.subscribeContent
import de.connect2x.trixnity.crypto.driver.CryptoDriver
import de.connect2x.trixnity.crypto.olm.DecryptedOlmEventContainer
import de.connect2x.trixnity.crypto.olm.OlmEncryptionService
import de.connect2x.trixnity.crypto.olm.OlmEventHandler
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val log = Logger("de.connect2x.trixnity.client.verification.ActiveDeviceVerification")

interface ActiveDeviceVerification : ActiveVerification
class ActiveDeviceVerificationImpl(
    request: VerificationRequestToDeviceEventContent,
    requestIsOurs: Boolean,
    ownUserId: UserId,
    ownDeviceId: String,
    theirUserId: UserId,
    theirDeviceId: String? = null,
    private val theirDeviceIds: Set<String> = setOf(),
    supportedMethods: Set<VerificationMethod>,
    private val api: MatrixClientServerApiClient,
    private val olmEventHandler: OlmEventHandler,
    private val olmEncryptionService: OlmEncryptionService,
    keyTrust: KeyTrustService,
    keyStore: KeyStore,
    private val clock: Clock,
    driver: CryptoDriver,
) : ActiveDeviceVerification, ActiveVerificationImpl(
    request,
    requestIsOurs,
    ownUserId,
    ownDeviceId,
    theirUserId,
    theirDeviceId,
    request.timestamp,
    supportedMethods,
    null,
    request.transactionId,
    keyStore,
    keyTrust,
    api.json,
    driver,
) {
    override suspend fun sendVerificationStep(step: VerificationStep) {
        log.debug { "send verification step $step" }
        val theirDeviceId = this.theirDeviceId
        val theirDeviceIds =
            if (theirDeviceId == null && step is VerificationCancelEventContent) theirDeviceIds
            else setOfNotNull(theirDeviceId)

        if (theirDeviceIds.isNotEmpty()) {
            val encryptedSteps =
                olmEncryptionService.encryptOlm(step, theirDeviceIds.map { theirUserId to it }.toSet())
                    .mapValues { it.value.getOrNull() ?: step }
                    .entries.groupBy { it.key.first }
                    .mapValues { it.value.associate { it.key.second to it.value } }
            api.user.sendToDevice(encryptedSteps).getOrThrow()
        }
    }

    override suspend fun lifecycle() {
        val unsubscribeHandleVerificationStepEvents =
            api.sync.subscribeContent(subscriber = ::handleVerificationStepEvents)
        val unsubscribeHandleOlmDecryptedVerificationRequestEvents =
            olmEventHandler.subscribe(::handleOlmDecryptedVerificationRequestEvents)
        try {
            // we do this, because otherwise the timeline job could run infinite, when no new timeline event arrives
            while (isVerificationRequestActive(timestamp, clock, state.value)) {
                delay(1.seconds)
            }
            if (isVerificationTimedOut(timestamp, clock, state.value)) {
                cancel(Timeout, "verification timed out")
            }
        } finally {
            unsubscribeHandleVerificationStepEvents()
            unsubscribeHandleOlmDecryptedVerificationRequestEvents()
        }
    }

    private suspend fun handleVerificationStepEvents(event: ClientEvent<VerificationStep>) {
        if (event is ToDeviceEvent) handleVerificationStepEvent(event.content, event.sender)
    }

    private suspend fun handleOlmDecryptedVerificationRequestEvents(event: DecryptedOlmEventContainer) {
        val content = event.decrypted.content
        if (content is VerificationStep) handleVerificationStepEvent(content, event.decrypted.sender)
    }

    private suspend fun handleVerificationStepEvent(step: VerificationStep, sender: UserId) {
        val eventTransactionId = step.transactionId
        if (eventTransactionId != null && eventTransactionId == transactionId
            && isVerificationRequestActive(timestamp, clock, state.value)
        ) {
            if (step is VerificationReadyEventContent) {
                val cancelDeviceIds = theirDeviceIds - step.fromDevice
                if (cancelDeviceIds.isNotEmpty()) {
                    val cancelEvent =
                        VerificationCancelEventContent(Accepted, "accepted by other device", relatesTo, transactionId)
                    try {
                        val encryptedCancelEvents =
                            olmEncryptionService.encryptOlm(
                                cancelEvent,
                                cancelDeviceIds.map { theirUserId to it }.toSet()
                            )
                                .mapValues { it.value.getOrNull() ?: cancelEvent }
                                .entries.groupBy { it.key.first }
                                .mapValues { it.value.associate { it.key.second to it.value } }
                        api.user.sendToDevice(encryptedCancelEvents).getOrThrow()
                    } catch (error: Exception) {
                        log.warn(error) { "could not send cancel to other device ids ($cancelDeviceIds)" }
                    }
                }
            }
            handleVerificationStep(step, sender, false)
        }
    }
}
