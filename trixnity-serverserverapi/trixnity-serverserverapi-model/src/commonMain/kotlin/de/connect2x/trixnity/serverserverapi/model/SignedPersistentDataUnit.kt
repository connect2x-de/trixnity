package de.connect2x.trixnity.serverserverapi.model

import de.connect2x.trixnity.core.model.events.PersistentDataUnit
import de.connect2x.trixnity.core.model.keys.Signed

@Deprecated(
    message = "Use Signed key type directly",
    replaceWith = ReplaceWith("Signed<PersistentDataUnit<T>, String>")
)
typealias SignedPersistentDataUnit<T> = Signed<PersistentDataUnit<T>, String>

@Deprecated(
    message = "Use Signed key type directly",
    replaceWith = ReplaceWith("Signed<PersistentDataUnit.PersistentStateDataUnit<T>, String>")
)
typealias SignedPersistentStateDataUnit<T> = Signed<PersistentDataUnit.PersistentStateDataUnit<T>, String>

@Deprecated(
    message = "Use Signed key type directly",
    replaceWith = ReplaceWith("Signed<PersistentDataUnit.PersistentMessageDataUnit<T>, String>")
)
typealias SignedPersistentMessageDataUnit<T> = Signed<PersistentDataUnit.PersistentMessageDataUnit<T>, String>
