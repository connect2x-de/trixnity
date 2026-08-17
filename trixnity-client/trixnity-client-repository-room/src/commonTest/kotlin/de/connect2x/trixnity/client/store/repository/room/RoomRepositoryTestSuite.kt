package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite

class RoomRepositoryTestSuite :
    RepositoryTestSuite(
        repositoriesModule =
            RepositoriesModule.room(
                Room.inMemoryDatabaseBuilder<TrixnityRoomDatabase>().setDriver(BundledSQLiteDriver())
            )
    )
