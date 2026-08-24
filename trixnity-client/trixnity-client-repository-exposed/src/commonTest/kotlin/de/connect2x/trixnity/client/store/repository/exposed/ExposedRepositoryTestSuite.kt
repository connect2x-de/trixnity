package de.connect2x.trixnity.client.store.repository.exposed

import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.test.RepositoryTestSuite
import de.connect2x.trixnity.utils.nextString
import kotlin.random.Random
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase

class ExposedRepositoryTestSuite :
    RepositoryTestSuite(
        repositoriesModule =
            RepositoriesModule.exposed(
                R2dbcDatabase.connect(url = "r2dbc:h2:mem:///${Random.nextString(22)};DB_CLOSE_DELAY=-1;")
            )
    )
