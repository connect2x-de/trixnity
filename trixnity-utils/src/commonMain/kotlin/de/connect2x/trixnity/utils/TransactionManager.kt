package de.connect2x.trixnity.utils

interface TransactionManager<R : ReadTransaction, W : WriteTransaction> {
    suspend fun <T> readTransaction(block: suspend R.() -> T): T

    suspend fun <T> writeTransaction(block: suspend W.() -> T): T
}

// @RestrictsSuspension // uncomment this to find mis-usage of the API
interface ReadTransaction

// @RestrictsSuspension // uncomment this to find mis-usage of the API
interface WriteTransaction : ReadTransaction
