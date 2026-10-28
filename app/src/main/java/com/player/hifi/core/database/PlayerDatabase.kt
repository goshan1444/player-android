package com.player.hifi.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PlayerDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    companion object {
        private const val DATABASE_NAME = "player_library.db"

        @Volatile
        private var INSTANCE: PlayerDatabase? = null

        fun getInstance(context: Context): PlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlayerDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
