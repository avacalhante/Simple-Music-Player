package com.simplemobiletools.musicplayer.analytics.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_start")
data class PlayStart(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: Long,

    @ColumnInfo(name = "playlist_id")
    val playlistId: Int,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Int,

    /** How far into the track playback began, non-zero when a saved position was resumed. */
    @ColumnInfo(name = "start_position_seconds")
    val startPositionSeconds: Int = 0,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long = 0L
)
