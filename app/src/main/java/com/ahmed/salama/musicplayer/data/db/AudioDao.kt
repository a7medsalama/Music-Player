package com.ahmed.salama.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for performing database operations on [AudioEntity] objects.
 * Provides simple insert and query functionality. All inserts use
 * REPLACE strategy so that subsequent scans of the MediaStore will refresh
 * the cached data.
 */
@Dao
interface AudioDao {
    /**
     * Return all cached audio entities from the database. Returns an empty list
     * if nothing has been cached yet.
     */
    @Query("SELECT * FROM audio_items")
    fun getAll(): List<AudioEntity>

    /**
     * Insert a list of entities into the database, replacing on conflict.
     * The suspend modifier marks this as a long‑running operation.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entities: List<AudioEntity>)

    /**
     * Clear all entries from the cache. This isn't currently used, but may
     * be helpful if you want to force a rescan.
     */
    @Query("DELETE FROM audio_items")
    fun clear()
}