package com.simplemobiletools.musicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Holds the timestamp a track's content was first seen by the app, keyed by a hash of the file. It is stored separately from [Track] on purpose, as track
 * rows are re-created at every media scan, and the hash does not depend on the file's name or location. This keeps the "date added" sorting stable even
 * if files are moved, copied or otherwise touched in the file system.
 */
@Entity(tableName = "track_seen")
data class TrackSeen(
    @PrimaryKey @ColumnInfo(name = "hash") val hash: String,
    @ColumnInfo(name = "first_seen") val firstSeen: Int
)
