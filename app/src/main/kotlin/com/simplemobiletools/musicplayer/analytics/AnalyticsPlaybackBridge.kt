package com.simplemobiletools.musicplayer.analytics

import androidx.media3.common.MediaItem
import com.simplemobiletools.musicplayer.playback.PlaybackService

/**
 * Adapter between playback callbacks and [AnalyticsRecorder].
 * All writes are dispatched to [AnalyticsExecutor] so playback is never blocked on the database,
 * and the single-threaded executor keeps the recorded order of events.
 */
class AnalyticsPlaybackBridge(service: PlaybackService) {
    private val recorder = AnalyticsRecorder(service.applicationContext)

    @Volatile
    private var currentRootId: String = ""

    fun setRootContext(rootId: String) {
        currentRootId = rootId
    }

    fun onTrackStart(mediaItem: MediaItem) {
        val playlistId = SessionContext.playlistIdFor(currentRootId)
        val source = SessionContext.sourceFor(currentRootId)
        post { recorder.onTrackStart(mediaItem, playlistId, source) }
    }

    fun onTrackEnd(positionMs: Long? = null, durationMs: Long? = null) {
        post { recorder.onTrackEnd(positionMs, durationMs) }
    }

    fun onPositionUpdated(positionMs: Long, durationMs: Long) {
        post { recorder.onPositionChanged(positionMs, durationMs) }
    }

    fun close(positionMs: Long? = null, durationMs: Long? = null) {
        post {
            recorder.onTrackEnd(positionMs, durationMs)
            recorder.onPlaybackStopped()
        }
    }

    private fun post(action: () -> Unit) {
        AnalyticsExecutor.myExecutor.execute(action)
    }
}
