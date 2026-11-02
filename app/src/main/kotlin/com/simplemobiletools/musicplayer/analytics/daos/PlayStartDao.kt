package com.simplemobiletools.musicplayer.analytics.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

@Dao
interface PlayStartDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(start: PlayStart): Long

    @Query("DELETE FROM play_start")
    fun deleteAll()

    @Query("SELECT * FROM play_start ORDER BY started_at DESC")
    fun getAll(): List<PlayStart>

    @Query("SELECT * FROM play_start WHERE id = :id")
    fun getById(id: Long): PlayStart?

    @Query("UPDATE play_start SET duration_seconds = :durationSeconds WHERE id = :id")
    fun updateDuration(id: Long, durationSeconds: Int)

    @Query("SELECT COUNT(*) FROM play_start")
    fun getTotalPlayCount(): Int

    @Query("SELECT COUNT(DISTINCT track_id) FROM play_start")
    fun getDistinctTrackCount(): Int

    @Query("SELECT DISTINCT track_id FROM play_start")
    fun getDistinctTrackIds(): List<Long>

    /**
     * Tracks whose latest play is older than the cutoff. The cutoff has to be applied to the grouped maximum,
     * not in a WHERE clause: filtering the rows first would still report a track that was also played recently.
     */
    @Query("""
        SELECT track_id, MAX(started_at) AS last_played_at
        FROM play_start
        GROUP BY track_id
        HAVING MAX(started_at) < :cutoffEpochSeconds
        ORDER BY last_played_at ASC
        LIMIT :limit
    """)
    fun getTracksNotPlayedSince(cutoffEpochSeconds: Int, limit: Int): List<LastPlayedPerTrack>

    @Query("""
        SELECT track_id, COUNT(*) AS play_count
        FROM play_start
        GROUP BY track_id
        ORDER BY play_count DESC
        LIMIT :limit
    """)
    fun getMostPlayedTrackIds(limit: Int): List<MostPlayedTrack>

    @Query("""
        SELECT playlist_id, COUNT(*) AS play_count,
               SUM(
                   COALESCE(
                       (SELECT ended_at - started_at
                        FROM play_end
                        WHERE play_end.play_start_id = play_start.id
                          AND fully_played = 1),
                       0
                   )
               ) AS played_seconds
        FROM play_start
        WHERE playlist_id > 0
        GROUP BY playlist_id
        ORDER BY play_count DESC
    """)
    fun getPlaylistStats(): List<PlaylistPlayStats>

    @Query("""
        SELECT
            strftime('%Y-%m-%d', started_at, 'unixepoch', 'localtime') AS day,
            COUNT(*) AS play_count,
            SUM(
                MAX(
                    0,
                    COALESCE(
                        (SELECT position_at_end
                         FROM play_end
                         WHERE play_end.play_start_id = play_start.id),
                        0
                    ) - start_position_seconds
                )
            ) AS played_seconds
        FROM play_start
        GROUP BY day
        ORDER BY day DESC
        LIMIT :limit
    """)
    fun getDailyStats(limit: Int): List<DailyStats>

    @Query("""
        SELECT
            CAST(strftime('%w', started_at, 'unixepoch', 'localtime') AS INTEGER) AS day_of_week,
            COUNT(*) AS play_count
        FROM play_start
        GROUP BY day_of_week
        ORDER BY play_count DESC
    """)
    fun getDayOfWeekStats(): List<DayOfWeekStats>

    @Query("""
        SELECT track_id, MIN(started_at) AS first_played_at
        FROM play_start
        GROUP BY CAST(started_at / 86400 AS INTEGER)
        ORDER BY first_played_at
    """)
    fun getFirstTrackPerDay(): List<FirstTrackPerDay>

    @Query("""
        SELECT track_id, COUNT(*) AS repeat_count
        FROM (
            SELECT a.track_id,
                   a.started_at,
                   MIN(b.started_at) AS next_play_at
            FROM play_start a
            LEFT JOIN play_start b
                ON b.track_id = a.track_id
               AND b.started_at > a.started_at
            GROUP BY a.id
        )
        WHERE next_play_at > 0
          AND next_play_at - started_at <= :windowSeconds
        GROUP BY track_id
        ORDER BY repeat_count DESC
    """)
    fun getRepeatWithinWindowStats(windowSeconds: Int): List<RepeatTrackStats>

    @Query("""
        SELECT track_id, COUNT(*) AS play_count
        FROM play_start
        WHERE started_at >= :sinceEpochSeconds
        GROUP BY track_id
        ORDER BY play_count DESC
        LIMIT :limit
    """)
    fun getMostPlayedTrackIdsSince(sinceEpochSeconds: Int, limit: Int): List<MostPlayedTrack>

    @Query("""
        SELECT track_id, COUNT(*) AS play_count
        FROM play_start
        WHERE started_at >= :sinceEpochSeconds
          AND started_at < :untilEpochSeconds
        GROUP BY track_id
        ORDER BY play_count DESC
        LIMIT :limit
    """)
    fun getMostPlayedTrackIdsInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int): List<MostPlayedTrack>

    @Query("""
        SELECT track_id, COUNT(*) AS play_count
        FROM play_start
        WHERE started_at >= :dayStartSeconds
          AND started_at < :dayEndSeconds
        GROUP BY track_id
        ORDER BY play_count DESC
    """)
    fun getMostPlayedTrackIdsOnDate(dayStartSeconds: Int, dayEndSeconds: Int): List<MostPlayedTrack>

    @Query("""
        SELECT track_id, MAX(started_at) AS last_played_at
        FROM play_start
        GROUP BY track_id
    """)
    fun getLastPlayedPerTrack(): List<LastPlayedPerTrack>

    @Query("SELECT COUNT(*) FROM play_start WHERE track_id = :trackId")
    fun getPlayCountForTrack(trackId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM play_start
        WHERE track_id = :trackId
          AND started_at >= :sinceEpochSeconds
    """)
    fun getPlayCountForTrackSince(trackId: Long, sinceEpochSeconds: Int): Int

    @Query("SELECT EXISTS(SELECT 1 FROM play_start WHERE track_id = :trackId LIMIT 1)")
    fun hasEverPlayedTrack(trackId: Long): Boolean

    @Query("""
        SELECT COUNT(*) FROM play_start
        WHERE track_id = :trackId
          AND started_at >= :sinceEpochSeconds
    """)
    fun getPlayedRecentlyCount(trackId: Long, sinceEpochSeconds: Int): Int
}
