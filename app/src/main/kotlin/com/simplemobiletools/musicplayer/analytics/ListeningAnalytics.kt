package com.simplemobiletools.musicplayer.analytics

import android.content.Context
import com.simplemobiletools.musicplayer.analytics.daos.DailyStats
import com.simplemobiletools.musicplayer.analytics.daos.DayOfWeekStats
import com.simplemobiletools.musicplayer.analytics.daos.EngagementCounts
import com.simplemobiletools.musicplayer.analytics.daos.FirstTrackPerDay
import com.simplemobiletools.musicplayer.analytics.daos.LastPlayedPerTrack
import com.simplemobiletools.musicplayer.analytics.daos.ListeningSessionDao
import com.simplemobiletools.musicplayer.analytics.daos.MostPlayedTrack
import com.simplemobiletools.musicplayer.analytics.daos.PlayEndDao
import com.simplemobiletools.musicplayer.analytics.daos.SessionInfo
import com.simplemobiletools.musicplayer.analytics.daos.TrackPartialStopStats
import com.simplemobiletools.musicplayer.analytics.daos.PlaylistPlayStats
import com.simplemobiletools.musicplayer.analytics.daos.PlaylistListeningTime
import com.simplemobiletools.musicplayer.analytics.daos.TrackListeningTime
import com.simplemobiletools.musicplayer.analytics.daos.WeekdaySessionWindow
import com.simplemobiletools.musicplayer.analytics.entities.ListeningSession
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

object ListeningAnalytics {
    const val QUICK_SKIP_SECONDS = 15
    const val REPEAT_WINDOW_SECONDS = 10 * 60
    const val NOT_PLAYED_RECENTLY_DAYS = 90

    /** Idle gap that ends a listening session: starting a track this long after the previous one begins a new session. */
    const val SESSION_GAP_SECONDS = 5 * 60

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
    private val sessionDao = db.listeningSessionDao()

    // ---- raw persistence ----

    fun recordPlayStart(trackId: Long, playlistId: Int, source: String, startedAt: Int, durationSeconds: Int, sessionId: Long = 0L): Long {
        return playStartDao.insert(
            PlayStart(
                trackId = trackId,
                playlistId = playlistId,
                source = source,
                startedAt = startedAt,
                durationSeconds = durationSeconds,
                sessionId = sessionId
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

    // ---- sessions ----

    fun openSession(startedAt: Int): Long = sessionDao.insert(ListeningSession(startedAt = startedAt))

    fun closeSession(sessionId: Long, endedAt: Int) {
        if (sessionId > 0) {
            sessionDao.closeSession(sessionId, endedAt)
        }
    }

    /**
     * Closes an open session whose last listening activity is older than the session gap, so the stats screen can
     * show it. Its close marker is that last activity, so a late sweep does not lengthen the session.
     */
    fun closeStaleSessions() = closeStaleSessions(nowSeconds())

    fun closeStaleSessions(nowSec: Int) {
        val open = sessionDao.getOpenSessionState() ?: return
        if (nowSec - open.lastActivityAt > ListeningAnalytics.SESSION_GAP_SECONDS) {
            sessionDao.closeSession(open.id, open.lastActivityAt)
        }
    }

    /**
     * The open session to continue, or null when there is none or its window has elapsed.
     * A session stays open across an app restart or a short stop, so a listening stretch is not
     * split into several sessions by a small gap.
     */
    fun resumeOpenSession(nowSec: Int): Long? {
        closeStaleSessions(nowSec)
        return sessionDao.getOpenSessionState()?.id
    }

    fun getClosedSessionCount(): Int = sessionDao.getClosedSessionCount()

    /** Closed sessions, newest first, with their length and number of tracks. */
    fun getSessionInfo(): List<SessionInfo> = sessionDao.getSessionInfo().map {
        it.copy(lengthSeconds = it.lengthSeconds.coerceAtLeast(0))
    }

    /** Average listening window per weekday, from the closed sessions. */
    fun getWeekdaySessionWindows(): List<WeekdaySessionWindow> = sessionDao.getWeekdaySessionWindows()

    // ---- basic totals ----

    fun totalPlayCount(): Int = playStartDao.getTotalPlayCount()
    fun distinctTrackCount(): Int = playStartDao.getDistinctTrackCount()
    fun getPlayedTrackIds(): List<Long> = playStartDao.getDistinctTrackIds()

    /**
     * Sums how far each ended play actually got, clamped to the track duration.
     * Plays that never ended have no recorded position and are left out.
     */
    fun totalListeningSeconds(): Long = playEndDao.getTotalListenedSeconds()

    // ---- completion / skip engagement ----

    fun getEngagementCounts(): EngagementCounts = playEndDao.getEngagementCounts()

    fun getCompletionRate(): Double = rateOf { it.fullyPlayedCount }

    fun getSkipRate(): Double = rateOf { it.skippedCount }

    fun getQuickSkipRate(): Double = rateOf { it.quickSkipCount }

    private fun rateOf(count: (EngagementCounts) -> Int): Double {
        val engagement = playEndDao.getEngagementCounts()
        if (engagement.endCount == 0) return 0.0
        return count(engagement).toDouble() / engagement.endCount.toDouble()
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

    /** Like [getMostPlayedTrackIdsInPeriod], keeping each track's play count so a summary can show it. */
    fun getMostPlayedTracksInPeriod(sinceEpochSeconds: Int, untilEpochSeconds: Int, limit: Int = 50): List<MostPlayedTrack> =
        playStartDao.getMostPlayedTrackIdsInPeriod(sinceEpochSeconds, untilEpochSeconds, limit)

    fun getMostPlayedTrackIdOnDate(dayStartSeconds: Int): Long? =
        playStartDao.getMostPlayedTrackIdsOnDate(dayStartSeconds, dayStartSeconds + 86400).firstOrNull()?.trackId

    fun getRepeatWithinWindowStats(): List<Long> =
        playStartDao.getRepeatWithinWindowStats(ListeningAnalytics.REPEAT_WINDOW_SECONDS).map { it.trackId }

    // ---- time by period ----

    fun getDailyStats(limit: Int = 14): List<DailyStats> = playStartDao.getDailyStats(limit)

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

    /** Played tracks whose most recent play is older than the threshold, oldest first. */
    fun getTracksNotPlayedSince(days: Int = ListeningAnalytics.NOT_PLAYED_RECENTLY_DAYS, limit: Int = 20): List<LastPlayedPerTrack> {
        val cutoff = (nowSeconds() - days * 86400L).toInt()
        return playStartDao.getTracksNotPlayedSince(cutoff, limit)
    }

    fun isPlayedRecently(trackId: Long): Boolean {
        val cutoff = nowSeconds() - ListeningAnalytics.NOT_PLAYED_RECENTLY_DAYS * 86400L
        return playStartDao.getPlayedRecentlyCount(trackId, cutoff.toInt()) > 0
    }

    // ---- helpers ----

    private fun nowSeconds(): Int = (System.currentTimeMillis() / 1000L).toInt()
}
