package de.connect2x.trixnity.client.store.repository.indexeddb

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite
import de.connect2x.trixnity.utils.nextString
import kotlin.random.Random

class IndexedDBRepositoryTestSuite :
    RepositoryTestSuite(
        repositoriesModule = RepositoriesModule { RepositoriesModule.indexedDB(Random.nextString(22)).create() }
    )
