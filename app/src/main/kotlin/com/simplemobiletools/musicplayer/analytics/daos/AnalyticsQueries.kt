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

data class DayOfWeekStats(
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "play_count") val playCount: Int
)

/**
 * Average listening window of the closed sessions that started on one weekday.
 * [startMinutes] is the mean local minute-of-day of the session starts (0..1439),
 * [lengthSeconds] the mean session length.
 */
data class WeekdaySessionWindow(
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_minutes") val startMinutes: Double,
    @ColumnInfo(name = "length_seconds") val lengthSeconds: Double,
    @ColumnInfo(name = "session_count") val sessionCount: Int
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

/**
 * How reliably a track gets listened to the end, over the plays of it that ended.
 * A play still in progress has no end row and is not counted, so pausing does not lower the rate.
 */
data class TrackCompletionStats(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "end_count") val endCount: Int,
    @ColumnInfo(name = "fully_played_count") val fullyPlayedCount: Int
) {
    val completionRate: Double get() = if (endCount == 0) 0.0 else fullyPlayedCount.toDouble() / endCount
}

data class TrackListeningTime(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long,
    @ColumnInfo(name = "end_count") val endCount: Int
)

/**
 * The newest open session and the epoch seconds of its last listening activity,
 * which is either the end of its last play or the start of a play that never ended.
 */
data class OpenSessionState(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Int
)

data class SessionInfo(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "started_at") val startedAt: Int,
    @ColumnInfo(name = "length_seconds") val lengthSeconds: Int,
    @ColumnInfo(name = "track_count") val trackCount: Int
)

data class PlaylistListeningTime(
    @ColumnInfo(name = "playlist_id") val playlistId: Int,
    @ColumnInfo(name = "played_seconds") val playedSeconds: Long,
    @ColumnInfo(name = "end_count") val endCount: Int
)
