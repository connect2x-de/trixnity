package de.connect2x.trixnity.client.store.repository.room

import androidx.room.Room
import androidx.room.RoomDatabase

actual fun randomDatabaseBuilder(): RoomDatabase.Builder<TrixnityRoomDatabase> =
    Room.inMemoryDatabaseBuilder<TrixnityRoomDatabase>()
