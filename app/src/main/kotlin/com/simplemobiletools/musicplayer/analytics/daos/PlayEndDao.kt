package com.simplemobiletools.musicplayer.analytics.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd

@Dao
interface PlayEndDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(end: PlayEnd): Long

    @Query("DELETE FROM play_end")
    fun deleteAll()

    /**
     * Records an end only while the play it belongs to still exists. The stats can be cleared or replaced while
     * a track is playing, and an end row without a play would still be counted by the engagement queries.
     */
    @Query("""
        INSERT INTO play_end (play_start_id, ended_at, position_at_end, fully_played, skipped, quick_skip)
        SELECT :playStartId, :endedAt, :positionAtEnd, :fullyPlayed, :skipped, :quickSkip
        WHERE EXISTS (SELECT 1 FROM play_start WHERE id = :playStartId)
    """)
    fun insertForExistingPlay(playStartId: Long, endedAt: Int, positionAtEnd: Int, fullyPlayed: Int, skipped: Int, quickSkip: Int)

    @Query("SELECT * FROM play_end ORDER BY ended_at DESC")
    fun getAll(): List<PlayEnd>

    @Query("SELECT COUNT(*) FROM play_end")
    fun getTotalEndCount(): Int

    @Query("""
        SELECT COUNT(*) AS end_count,
               COALESCE(SUM(CASE WHEN fully_played = 1 THEN 1 ELSE 0 END), 0) AS fully_played_count,
               COALESCE(SUM(CASE WHEN skipped = 1 THEN 1 ELSE 0 END), 0) AS skipped_count,
               COALESCE(SUM(CASE WHEN quick_skip = 1 THEN 1 ELSE 0 END), 0) AS quick_skip_count
        FROM play_end
    """)
    fun getEngagementCounts(): EngagementCounts

    @Query("""
        SELECT COALESCE(
                   SUM(
                       CASE WHEN p.duration_seconds > 0
                            THEN MAX(0, MIN(e.position_at_end, p.duration_seconds) - p.start_position_seconds)
                            ELSE MAX(0, e.position_at_end - p.start_position_seconds)
                       END
                   ),
                   0
               )
        FROM play_end e
        JOIN play_start p ON p.id = e.play_start_id
    """)
    fun getTotalListenedSeconds(): Long

    @Query("SELECT COUNT(*) FROM play_end WHERE fully_played = 1")
    fun getFullyPlayedCount(): Int

    @Query("SELECT COUNT(*) FROM play_end WHERE skipped = 1")
    fun getSkippedCount(): Int

    @Query("SELECT COUNT(*) FROM play_end WHERE quick_skip = 1")
    fun getQuickSkippedCount(): Int

    @Query("""
        SELECT p.track_id AS track_id,
               COUNT(e.id) AS end_count,
               SUM(CASE WHEN e.fully_played = 1 THEN 1 ELSE 0 END) AS fully_played_count,
               SUM(CASE WHEN e.skipped = 1 THEN 1 ELSE 0 END) AS skipped_count,
               SUM(CASE WHEN e.quick_skip = 1 THEN 1 ELSE 0 END) AS quick_skip_count,
               AVG(e.position_at_end * 1.0 / MAX(p.duration_seconds, 1)) AS avg_position_ratio
        FROM play_end e
        JOIN play_start p ON p.id = e.play_start_id
        WHERE p.duration_seconds > 0
        GROUP BY p.track_id
        HAVING end_count >= :minPlays
        ORDER BY avg_position_ratio ASC
        LIMIT :limit
    """)
    fun getTracksStoppedPartway(minPlays: Int, limit: Int): List<TrackPartialStopStats>

    @Query("""
        SELECT
            p.track_id AS track_id,
            COALESCE(SUM(MAX(0, e.position_at_end - p.start_position_seconds)), 0) AS played_seconds,
            COUNT(e.id) AS end_count
        FROM play_start p
        LEFT JOIN play_end e ON e.play_start_id = p.id
        WHERE p.started_at >= :sinceEpochSeconds
          AND p.started_at < :untilEpochSeconds
        GROUP BY p.track_id
        ORDER BY played_seconds DESC
        LIMIT :limit
    """)
    fun getTrackListeningTimeInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int): List<TrackListeningTime>

    @Query("""
        SELECT
            p.playlist_id AS playlist_id,
            COALESCE(SUM(MAX(0, e.position_at_end - p.start_position_seconds)), 0) AS played_seconds,
            COUNT(e.id) AS end_count
        FROM play_start p
        LEFT JOIN play_end e ON e.play_start_id = p.id
        WHERE p.playlist_id > 0
          AND p.started_at >= :sinceEpochSeconds
          AND p.started_at < :untilEpochSeconds
        GROUP BY p.playlist_id
        ORDER BY played_seconds DESC
        LIMIT :limit
    """)
    fun getPlaylistListeningTimeInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int): List<PlaylistListeningTime>
}
