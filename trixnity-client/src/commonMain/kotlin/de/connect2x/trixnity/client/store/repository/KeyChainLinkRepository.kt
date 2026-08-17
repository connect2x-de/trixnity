package de.connect2x.trixnity.client.store.repository

import de.connect2x.trixnity.client.store.KeyChainLink
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.keys.Key
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

interface KeyChainLinkRepository {
    context(transaction: WriteTransaction)
    suspend fun save(keyChainLink: KeyChainLink)

    context(transaction: ReadTransaction)
    suspend fun getBySigningKey(signingUserId: UserId, signingKey: Key.Ed25519Key): Set<KeyChainLink>

    context(transaction: WriteTransaction)
    suspend fun deleteBySignedKey(signedUserId: UserId, signedKey: Key.Ed25519Key)

    context(transaction: WriteTransaction)
    suspend fun deleteAll()
}
