package com.simplemobiletools.musicplayer.analytics.daos

import androidx.room.ColumnInfo

data class MostPlayedTrack(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "play_count") val playCount: Int
)

data class RepeatTrackStats(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int
)

data class PlaylistPlayStats(
    @ColumnInfo(name = "playlist_id") val playlistId: Int,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long
)

data class DailyStats(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long
)

data class HourlyStats(
    @ColumnInfo(name = "hour") val hour: Int,
    @ColumnInfo(name = "play_count") val playCount: Int
)

data class DayOfWeekStats(
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "play_count") val playCount: Int
)

data class FirstTrackPerDay(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "first_played_at") val firstPlayedAt: Int
)

data class LastPlayedPerTrack(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Int
)

data class EngagementCounts(
    @ColumnInfo(name = "end_count") val endCount: Int,
    @ColumnInfo(name = "fully_played_count") val fullyPlayedCount: Int,
    @ColumnInfo(name = "skipped_count") val skippedCount: Int,
    @ColumnInfo(name = "quick_skip_count") val quickSkipCount: Int
)

data class TrackPartialStopStats(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "end_count") val endCount: Int,
    @ColumnInfo(name = "fully_played_count") val fullyPlayedCount: Int,
    @ColumnInfo(name = "skipped_count") val skippedCount: Int,
    @ColumnInfo(name = "quick_skip_count") val quickSkipCount: Int,
    @ColumnInfo(name = "avg_position_ratio") val avgPositionRatio: Double
)

data class TrackListeningTime(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long,
    @ColumnInfo(name = "end_count") val endCount: Int
)

data class PlaylistListeningTime(
    @ColumnInfo(name = "playlist_id") val playlistId: Int,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long,
    @ColumnInfo(name = "end_count") val endCount: Int
)
