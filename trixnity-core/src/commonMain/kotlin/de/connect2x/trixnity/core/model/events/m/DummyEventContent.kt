package de.connect2x.trixnity.core.model.events.m

import de.connect2x.trixnity.core.model.events.ToDeviceEventContent
import kotlinx.serialization.Serializable

/** @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mdummy">matrix spec</a> */
@Serializable data object DummyEventContent : ToDeviceEventContent
