package com.simplemobiletools.musicplayer.analytics

import android.content.Context
import com.simplemobiletools.musicplayer.analytics.daos.DailyStats
import com.simplemobiletools.musicplayer.analytics.daos.DayOfWeekStats
import com.simplemobiletools.musicplayer.analytics.daos.FirstTrackPerDay
import com.simplemobiletools.musicplayer.analytics.daos.HourlyStats
import com.simplemobiletools.musicplayer.analytics.daos.LastPlayedPerTrack
import com.simplemobiletools.musicplayer.analytics.daos.MostPlayedTrack
import com.simplemobiletools.musicplayer.analytics.daos.PlayEndDao
import com.simplemobiletools.musicplayer.analytics.daos.TrackPartialStopStats
import com.simplemobiletools.musicplayer.analytics.daos.PlaylistPlayStats
import com.simplemobiletools.musicplayer.analytics.daos.PlaylistListeningTime
import com.simplemobiletools.musicplayer.analytics.daos.TrackListeningTime
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

object ListeningAnalytics {
    const val QUICK_SKIP_SECONDS = 15
    const val REPEAT_WINDOW_SECONDS = 10 * 60
    const val NOT_PLAYED_RECENTLY_DAYS = 90

    @Volatile
    private var store: ListeningAnalyticsStore? = null

    fun getInstance(context: Context): ListeningAnalyticsStore {
        return store ?: synchronized(this) {
            store ?: ListeningAnalyticsStore(context.applicationContext).also { store = it }
        }
    }
}

class ListeningAnalyticsStore(private val context: Context) {
    private val db: AnalyticsDatabase by lazy { AnalyticsDatabase.getInstance(context) }
    private val playStartDao = db.playStartDao()
    private val playEndDao = db.playEndDao()

    // ---- raw persistence ----

    fun recordPlayStart(trackId: Long, playlistId: Int, source: String, startedAt: Int, durationSeconds: Int): Long {
        return playStartDao.insert(
            PlayStart(
                trackId = trackId,
                playlistId = playlistId,
                source = source,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                sessionId = 0L
            )
        )
    }

    fun updatePlayStartDuration(playStartId: Long, durationSeconds: Int) {
        playStartDao.updateDuration(playStartId, durationSeconds)
    }

    fun recordPlayEnd(
        playStartId: Long,
        endedAt: Int,
        positionAtEnd: Int,
        fullyPlayed: Boolean,
        skipped: Boolean,
        quickSkip: Boolean
    ) {
        playEndDao.insert(
            PlayEnd(
                playStartId = playStartId,
                endedAt = endedAt,
                positionAtEnd = positionAtEnd,
                fullyPlayed = if (fullyPlayed) 1 else 0,
                skipped = if (skipped) 1 else 0,
                quickSkip = if (quickSkip) 1 else 0
            )
        )
    }

    // ---- basic totals ----

    fun totalPlayCount(): Int = playStartDao.getTotalPlayCount()
    fun distinctTrackCount(): Int = playStartDao.getDistinctTrackCount()
    fun totalListeningSeconds(): Long {
        // Sum completed play durations only; unfinished plays are excluded on purpose.
        val ends = playEndDao.getAll()
        return ends.sumOf { (it.endedAt - lookupStart(it.playStartId).startedAt).toLong() }
    }

    // ---- completion / skip engagement ----

    fun getCompletionRate(): Double {
        val total = playEndDao.getTotalEndCount()
        if (total == 0) return 0.0
        val fullyPlayed = playEndDao.getFullyPlayedCount()
        return fullyPlayed.toDouble() / total.toDouble()
    }

    fun getSkipRate(): Double {
        val total = playEndDao.getTotalEndCount()
        if (total == 0) return 0.0
        val skipped = playEndDao.getSkippedCount()
        return skipped.toDouble() / total.toDouble()
    }

    fun getQuickSkipRate(): Double {
        val total = playEndDao.getTotalEndCount()
        if (total == 0) return 0.0
        val quickSkipped = playEndDao.getQuickSkippedCount()
        return quickSkipped.toDouble() / total.toDouble()
    }

    fun getTracksStoppedPartway(minPlays: Int = 3, limit: Int = 50): List<TrackPartialStopStats> =
        playEndDao.getTracksStoppedPartway(minPlays, limit)

    // ---- most played ----

    fun getMostPlayedTracks(limit: Int = 20): List<MostPlayedTrack> = playStartDao.getMostPlayedTrackIds(limit)

    fun getMostPlayedTrackIds(limit: Int = 50): List<Long> =
        playStartDao.getMostPlayedTrackIds(limit).map { it.trackId }

    fun getMostPlayedTrackIdsSince(epochSeconds: Int, limit: Int = 50): List<Long> =
        playStartDao.getMostPlayedTrackIdsSince(epochSeconds, limit).map { it.trackId }

    fun getMostPlayedTrackIdsInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int = 50): List<Long> =
        playStartDao.getMostPlayedTrackIdsInPeriod(sinceEpochSeconds, untilEpochSeconds, limit).map { it.trackId }

    fun getMostPlayedTrackIdOnDate(dayStartSeconds: Int): Long? =
        playStartDao.getMostPlayedTrackIdsOnDate(dayStartSeconds, dayStartSeconds + 86400).firstOrNull()?.trackId

    fun getRepeatWithinWindowStats(): List<Long> =
        playStartDao.getRepeatWithinWindowStats(ListeningAnalytics.REPEAT_WINDOW_SECONDS).map { it.trackId }

    // ---- time by period ----

    fun getDailyStats(limit: Int = 14): List<DailyStats> = playStartDao.getDailyStats(limit)

    fun getHourlyStats(): List<HourlyStats> = playStartDao.getHourlyStats()

    fun getDayOfWeekStats(): List<DayOfWeekStats> = playStartDao.getDayOfWeekStats()

    fun getFirstTrackPerDay(): List<FirstTrackPerDay> = playStartDao.getFirstTrackPerDay()

    fun getTracksListenedInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int = 50): List<TrackListeningTime> =
        playEndDao.getTrackListeningTimeInPeriod(sinceEpochSeconds, untilEpochSeconds, limit)

    // ---- playlist listening ----

    fun getPlaylistStats(): List<PlaylistPlayStats> = playStartDao.getPlaylistStats()

    fun getPlaylistListeningTimeInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int = 50): List<PlaylistListeningTime> =
        playEndDao.getPlaylistListeningTimeInPeriod(sinceEpochSeconds, untilEpochSeconds, limit)

    // ---- discovery / forgotten ----

    fun hasEverPlayedTrack(trackId: Long): Boolean = playStartDao.hasEverPlayedTrack(trackId)

    fun getLastPlayedPerTrack(): List<LastPlayedPerTrack> = playStartDao.getLastPlayedPerTrack()

    fun isPlayedRecently(trackId: Long): Boolean {
        val cutoff = nowSeconds() - ListeningAnalytics.NOT_PLAYED_RECENTLY_DAYS * 86400L
        return playStartDao.getPlayedRecentlyCount(trackId, cutoff.toInt()) > 0
    }

    // ---- helpers ----

    private fun nowSeconds(): Int = (System.currentTimeMillis() / 1000L).toInt()

    private fun lookupStart(playStartId: Long): PlayStart =
        playStartDao.getById(playStartId)
            ?: PlayStart(trackId = 0L, playlistId = 0, source = "", startedAt = 0)
}
