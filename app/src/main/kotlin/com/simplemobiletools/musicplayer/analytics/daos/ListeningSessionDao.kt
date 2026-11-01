package com.simplemobiletools.musicplayer.analytics.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.analytics.entities.ListeningSession

@Dao
interface ListeningSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(session: ListeningSession): Long

    /**
     * The newest open session with its last listening activity, used to continue a session across a process
     * death or an app restart while the session gap window is still alive.
     * A play that ended contributes its end time, a play that never ended its start plus its known duration,
     * and a session without plays its own start.
     */
    @Query("""
        SELECT s.id AS id,
               MAX(
                   COALESCE(
                       e.ended_at,
                       p.started_at + CASE WHEN p.duration_seconds > 0 THEN p.duration_seconds ELSE 0 END,
                       p.started_at,
                       s.started_at
                   )
               ) AS last_activity_at
        FROM listening_sessions AS s
        LEFT JOIN play_start AS p ON p.session_id = s.id
        LEFT JOIN play_end AS e ON e.play_start_id = p.id
        WHERE s.ended_at = 0
        GROUP BY s.id
        ORDER BY s.started_at DESC
        LIMIT 1
    """)
    fun getOpenSessionState(): OpenSessionState?

    /** Sets the close marker once; a second close of the same session must not move it. */
    @Query("UPDATE listening_sessions SET ended_at = :endedAt WHERE id = :id AND ended_at = 0")
    fun closeSession(id: Long, endedAt: Int)

    @Query("SELECT COUNT(*) FROM listening_sessions WHERE ended_at > 0")
    fun getClosedSessionCount(): Int

    /**
     * Closed sessions with their wall-clock length and play count.
     * The length runs from the session start to the last recorded activity (the end of its last play),
     * so idle time between the last play and the moment the session got closed is not counted.
     * A play that never ended (the process went away mid-track) contributes its start plus its known duration,
     * clamped to the moment the session was closed so an interrupted session cannot run into the next one.
     */
    @Query("""
        SELECT s.id AS id,
               s.started_at AS started_at,
               MIN(
                   MAX(COALESCE(e.ended_at, p.started_at + CASE WHEN p.duration_seconds > 0 THEN p.duration_seconds ELSE 0 END)),
                   s.ended_at
               ) - s.started_at AS length_seconds,
               (SELECT COUNT(*) FROM play_start AS c WHERE c.session_id = s.id) AS track_count
        FROM listening_sessions AS s
        LEFT JOIN play_start AS p ON p.session_id = s.id
        LEFT JOIN play_end AS e ON e.play_start_id = p.id
        WHERE s.ended_at > 0
        GROUP BY s.id
        ORDER BY s.started_at DESC
    """)
    fun getSessionInfo(): List<SessionInfo>

    /**
     * Average listening window per weekday, over closed sessions only.
     * Per session this reuses the same length as [getSessionInfo] (last activity clamped to the close marker),
     * then averages the local minute-of-day of the start and that length. Sunday = 0, as in `%w`.
     */
    @Query("""
        SELECT day_of_week,
               AVG(start_minutes) AS start_minutes,
               AVG(length_seconds) AS length_seconds,
               COUNT(*) AS session_count
        FROM (
            SELECT CAST(strftime('%w', s.started_at, 'unixepoch', 'localtime') AS INTEGER) AS day_of_week,
                   CAST(strftime('%H', s.started_at, 'unixepoch', 'localtime') AS INTEGER) * 60 +
                   CAST(strftime('%M', s.started_at, 'unixepoch', 'localtime') AS INTEGER) AS start_minutes,
                   MAX(
                       0,
                       MIN(
                           COALESCE(
                               MAX(COALESCE(e.ended_at, p.started_at + CASE WHEN p.duration_seconds > 0 THEN p.duration_seconds ELSE 0 END)),
                               s.started_at
                           ),
                           s.ended_at
                       ) - s.started_at
                   ) AS length_seconds
            FROM listening_sessions AS s
            LEFT JOIN play_start AS p ON p.session_id = s.id
            LEFT JOIN play_end AS e ON e.play_start_id = p.id
            WHERE s.ended_at > 0
            GROUP BY s.id
        )
        GROUP BY day_of_week
        ORDER BY day_of_week
    """)
    fun getWeekdaySessionWindows(): List<WeekdaySessionWindow>
}
