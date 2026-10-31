package com.simplemobiletools.musicplayer.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.models.TrackHashCache
import com.simplemobiletools.musicplayer.models.TrackSeen

@Dao
interface TrackSeenDao {
    @Query("SELECT * FROM track_seen")
    fun getAllSeen(): List<TrackSeen>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSeen(entries: List<TrackSeen>)

    @Query("SELECT * FROM track_hash_cache")
    fun getAllHashCache(): List<TrackHashCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertHashCache(entries: List<TrackHashCache>)

    @Query("SELECT path FROM track_hash_cache")
    fun getAllHashCachePaths(): List<String>

    @Query("DELETE FROM track_hash_cache WHERE path IN (:paths)")
    fun deleteHashCachePaths(paths: List<String>)
}
