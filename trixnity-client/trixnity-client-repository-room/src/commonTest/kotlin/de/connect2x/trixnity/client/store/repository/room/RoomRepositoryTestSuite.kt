package de.connect2x.trixnity.client.store.repository.room

import androidx.room3.Room
import de.connect2x.sqlitenity.encrypted.driver.EncryptedSQLiteDriver
import de.connect2x.sqlitenity.encrypted.driver.EncryptionKey
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite :
    RepositoryTestSuite(
        repositoriesModule =
            RepositoriesModule.room(
                Room.inMemoryDatabaseBuilder<TrixnityRoomDatabase>()
                    .setDriver(EncryptedSQLiteDriver(EncryptionKey.None))
            )
    )
