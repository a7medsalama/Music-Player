package com.ahmed.salama.musicplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database that holds the [AudioEntity] cache. The database is a
 * singleton to avoid having multiple open connections to disk. Use
 * [getInstance] to obtain a reference and call [audioDao] to access
 * the DAO methods.
 */
@Database(entities = [AudioEntity::class], version = 1, exportSchema = false)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao

    companion object {
        @Volatile
        private var INSTANCE: AudioDatabase? = null

        /**
         * Get a singleton instance of the [AudioDatabase]. If the database
         * doesn't exist yet, build it using the application context.
         */
        fun getInstance(context: Context): AudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AudioDatabase::class.java,
                    "audio_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}