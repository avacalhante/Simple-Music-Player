package com.simplemobiletools.musicplayer.analytics.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_end")
data class PlayEnd(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "play_start_id")
    val playStartId: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Int,

    @ColumnInfo(name = "position_at_end")
    val positionAtEnd: Int,

    @ColumnInfo(name = "fully_played")
    val fullyPlayed: Int,

    @ColumnInfo(name = "skipped")
    val skipped: Int,

    @ColumnInfo(name = "quick_skip")
    val quickSkip: Int
)
