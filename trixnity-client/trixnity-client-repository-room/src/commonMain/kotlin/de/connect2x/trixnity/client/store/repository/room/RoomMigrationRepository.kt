package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import de.connect2x.trixnity.client.store.repository.MigrationRepository
import de.connect2x.trixnity.utils.ReadTransaction
import de.connect2x.trixnity.utils.WriteTransaction

@Entity(tableName = "Migration") data class RoomMigration(@PrimaryKey val name: String, val metadata: String? = null)

@Dao
interface MigrationDao {
    @Query("SELECT * FROM Migration WHERE name = :name LIMIT 1") suspend fun get(name: String): RoomMigration?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: RoomMigration)

    @Query("DELETE FROM Migration WHERE name = :name") suspend fun delete(name: String)

    @Query("DELETE FROM Migration") suspend fun deleteAll()
}

internal class RoomMigrationRepository(db: TrixnityRoomDatabase) : MigrationRepository {

    private val dao = db.migration()

    context(transaction: ReadTransaction)
    override suspend fun get(key: String): String? = dao.get(key)?.metadata

    context(transaction: WriteTransaction)
    override suspend fun save(key: String, value: String) = dao.insert(RoomMigration(key, value))

    context(transaction: WriteTransaction)
    override suspend fun delete(key: String) = dao.delete(key)

    context(transaction: WriteTransaction)
    override suspend fun deleteAll() = dao.deleteAll()
}
