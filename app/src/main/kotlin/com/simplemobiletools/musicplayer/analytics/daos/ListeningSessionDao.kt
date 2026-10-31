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

    @Query("SELECT * FROM listening_sessions WHERE ended_at = 0 ORDER BY started_at DESC LIMIT 1")
    fun getOpenSession(): ListeningSession?

    @Query("UPDATE listening_sessions SET ended_at = :endedAt WHERE id = :id")
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
}
