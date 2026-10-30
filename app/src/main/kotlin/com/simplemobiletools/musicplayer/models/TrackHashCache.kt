package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Caches the content hash of a file path together with its size and last modification time. As long as those two values are unchanged the file does not
 * have to be read again, which keeps repeated media scans fast.
 */
@Entity(tableName = "track_hash_cache")
data class TrackHashCache(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "hash") val hash: String,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long
)
