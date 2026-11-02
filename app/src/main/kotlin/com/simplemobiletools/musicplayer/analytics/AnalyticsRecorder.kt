package com.simplemobiletools.musicplayer.analytics

import android.content.Context
import androidx.media3.common.MediaItem
import com.simplemobiletools.musicplayer.analytics.extensions.analytics

class AnalyticsRecorder(context: Context) {
    private val appContext = context.applicationContext

    // every recorder entry point already runs on AnalyticsExecutor, so the database is opened there, not on main
    private val analytics by lazy { appContext.analytics }

    private var currentPlayStartId: Long = 0L
    private var currentTrackId: Long = 0L
    private var currentPlaylistId: Int = 0
    private var currentSource: String = "unknown"
    private var currentStartedAt: Int = 0
    private var currentStartPositionSeconds: Int = 0
    private var lastKnownPositionMs: Long = 0L
    private var lastKnownDurationMs: Long = 0L
    private var persistedDurationSeconds: Int = 0

    fun onTrackStart(
        mediaItem: MediaItem,
        playlistId: Int,
        source: String,
        startPositionMs: Long
    ) {
        val trackId = mediaItem.mediaId.toLongOrNull() ?: 0L
        if (trackId > 0 && trackId == currentTrackId && currentPlayStartId > 0) {
            // the same attempt is already being recorded, e.g. a transient buffering change re-reports it
            return
        }

        currentPlayStartId = 0L
        currentTrackId = trackId
        currentPlaylistId = playlistId
        currentSource = source
        currentStartedAt = nowSeconds()
        currentStartPositionSeconds = (startPositionMs.coerceAtLeast(0L) / 1000L).toInt()
        lastKnownPositionMs = 0L
        // the duration is only known once the player has prepared the item, so it arrives via onPositionChanged
        lastKnownDurationMs = 0L
        persistedDurationSeconds = 0

        if (currentTrackId > 0) {
            val startId = analytics.recordPlayStart(
                trackId = currentTrackId,
                playlistId = currentPlaylistId,
                source = currentSource,
                startedAt = currentStartedAt,
                startPositionSeconds = currentStartPositionSeconds,
                // filled in by onPositionChanged once the player knows the real duration
                durationSeconds = 0,
                sessionId = ensureSession(currentStartedAt)
            )
            currentPlayStartId = startId
        }
    }

    /**
     * Returns the session the play belongs to, opening a new one when the last listening activity is older than
     * the session gap. The store owns that state, so the gap is measured from what is actually persisted: the end
     * of the last play, or the start plus the known duration of a play that never ended. Every play therefore
     * restarts the window, and a session left open by an app restart or a short stop is continued instead of
     * being split in two. Closing a session only marks it as finished; its length comes from its plays.
     */
    private fun ensureSession(nowSec: Int): Long {
        return analytics.resumeOpenSession(nowSec) ?: analytics.openSession(nowSec)
    }

    fun onPositionChanged(positionMs: Long, durationMs: Long) {
        lastKnownPositionMs = positionMs
        if (durationMs > 0L) {
            lastKnownDurationMs = durationMs
            val durationSeconds = durationMs.div(1000).toInt()
            if (currentPlayStartId > 0 && durationSeconds != persistedDurationSeconds) {
                persistedDurationSeconds = durationSeconds
                analytics.updatePlayStartDuration(currentPlayStartId, durationSeconds)
            }
        }
    }

    /**
     * Ends the current play attempt. The caller should pass the outgoing track's real position when it has it,
     * otherwise the last sampled values are used.
     */
    fun onTrackEnd(positionMs: Long? = null, durationMs: Long? = null) {
        if (currentPlayStartId <= 0 || currentTrackId <= 0) {
            return
        }

        if (durationMs != null && durationMs > 0L) {
            lastKnownDurationMs = durationMs
        }

        val nowSec = nowSeconds()
        val positionSec = (positionMs ?: lastKnownPositionMs).div(1000)
        val effectiveDurationSec = lastKnownDurationMs.div(1000)
        val listenedSec = (positionSec - currentStartPositionSeconds).coerceAtLeast(0L)

        // when the duration never became known the track cannot be judged as finished
        val fullyPlayed = effectiveDurationSec > 0 && positionSec >= (effectiveDurationSec - QUICK_SKIP_LEEWAY_SECONDS)
        val skipped = !fullyPlayed
        val quickSkip = skipped && listenedSec <= ListeningAnalytics.QUICK_SKIP_SECONDS

        analytics.recordPlayEnd(
            playStartId = currentPlayStartId,
            endedAt = nowSec,
            positionAtEnd = positionSec.toInt(),
            fullyPlayed = fullyPlayed,
            skipped = skipped,
            quickSkip = quickSkip
        )

        currentPlayStartId = 0L
        currentTrackId = 0L
        currentPlaylistId = 0
        currentSource = "unknown"
        currentStartedAt = 0
        currentStartPositionSeconds = 0
        lastKnownPositionMs = 0L
        lastKnownDurationMs = 0L
        persistedDurationSeconds = 0
    }

    private fun nowSeconds(): Int = (System.currentTimeMillis() / 1000L).toInt()

    companion object {
        private const val QUICK_SKIP_LEEWAY_SECONDS = 3
    }
}
