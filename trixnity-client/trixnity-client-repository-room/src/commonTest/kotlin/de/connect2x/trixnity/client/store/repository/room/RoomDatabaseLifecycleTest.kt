package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Room
import de.connect2x.sqlitenity.encrypted.driver.EncryptedSQLiteDriver
import de.connect2x.sqlitenity.encrypted.driver.EncryptionKey
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.StoreTransactionManager
import de.connect2x.trixnity.test.utils.TrixnityBaseTest
import de.connect2x.trixnity.test.utils.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class RoomDatabaseLifecycleTest : TrixnityBaseTest() {

    @Test
    fun `write finishes before database closes`() = runTest {
        val client = SimulatedMinimalClient(backgroundScope.coroutineContext + Dispatchers.Default)
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        var isCancelled = false
        client.scope.launch {
            client.transactionManager.writeTransaction {
                writeStarted.complete(Unit)
                finishWrite.await()
                client.database.account().insert(RoomAccount())
            }
            isCancelled = currentCoroutineContext().job.isCancelled
        }
        writeStarted.await()
        client.scope.coroutineContext.job.cancel("Client is closing")
        finishWrite.complete(Unit)
        client.scope.coroutineContext.job.join()
        assertTrue(isCancelled)
    }

    private class SimulatedMinimalClient private constructor(private val koin: Koin) {
        val transactionManager: StoreTransactionManager
            get() = koin.get<StoreTransactionManager>()

        val database: TrixnityRoomDatabase
            get() = koin.get<TrixnityRoomDatabase>()

        val scope: CoroutineScope
            get() = koin.get<CoroutineScope>()

        companion object {
            suspend operator fun invoke(coroutineContext: CoroutineContext): SimulatedMinimalClient {
                val coroutineScopeModule = module {
                    single<CoroutineScope> { CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job)) }
                }

                val repositoriesModule =
                    RepositoriesModule.room(
                            Room.inMemoryDatabaseBuilder<TrixnityRoomDatabase>()
                                .setDriver(EncryptedSQLiteDriver(EncryptionKey.None))
                        )
                        .create()

                val koin = koinApplication { modules(coroutineScopeModule, repositoriesModule) }.koin

                return SimulatedMinimalClient(koin)
            }
        }
    }
}
