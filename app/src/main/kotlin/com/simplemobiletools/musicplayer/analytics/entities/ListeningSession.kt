package com.simplemobiletools.musicplayer.analytics.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stretch of continuous listening. A session is opened by the first play after a quiet period and
 * closed when nothing is listened to for [com.simplemobiletools.musicplayer.analytics.ListeningAnalytics.SESSION_GAP_SECONDS].
 * Every play start and play end restarts that window, and a session left open by an app restart is continued.
 *
 * [endedAt] is only a closed-marker; the real session length is derived from the play_start/play_end
 * rows that point at this session, so paused time and idle gaps are not counted.
 */
@Entity(tableName = "listening_sessions")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Int,

    @ColumnInfo(name = "ended_at")
    val endedAt: Int = 0
)
