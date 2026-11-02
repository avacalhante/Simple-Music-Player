package com.simplemobiletools.musicplayer.playback.player

import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getPlaybackSetting
import com.simplemobiletools.musicplayer.helpers.PlaybackSetting
import com.simplemobiletools.musicplayer.playback.PlaybackService

@UnstableApi
internal fun PlaybackService.getPlayerListener() = object : Player.Listener {

    override fun onPlayerError(error: PlaybackException) = toast(com.simplemobiletools.commons.R.string.unknown_error_occurred, Toast.LENGTH_LONG)

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
            // items were added, removed or moved, so the complete queue has to be persisted.
            // note: EVENT_TRACKS_CHANGED is unrelated here, it fires when the current item's audio tracks change.
            updatePlaybackState()
        } else if (
            events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYLIST_METADATA_CHANGED
            )
        ) {
            // only the current track and its position changed, keep the rest of the queue as it is
            saveCurrentTrackProgress()
            analyticsBridge.onPositionUpdated(player.currentPosition, player.duration)
        }
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // this fires before onMediaItemTransition, so the outgoing track is still the current one.
        // only an actual media item change ends the play attempt, a seek within a track does not.
        val oldMediaId = oldPosition.mediaItem?.mediaId
        val newMediaId = newPosition.mediaItem?.mediaId
        if (oldMediaId != null && oldMediaId != newMediaId) {
            analyticsBridge.onTrackEnd(oldPosition.positionMs)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // customize repeat mode behaviour as the default behaviour doesn't align with our requirements.
        withPlayer {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (config.playbackSetting == PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
                    seekTo(0)
                    pause()
                }
            }
        }

        // A repeat transition restarts the same item, so the previous attempt ends here.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            analyticsBridge.onTrackEnd()
        }

        // Preparing or restoring the queue also fires a transition while the player is paused, so only count a
        // play once it actually starts. Starting playback later is caught by onIsPlayingChanged.
        if (mediaItem != null && player.isPlaying) {
            analyticsBridge.onTrackStart(mediaItem, player.currentPosition)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            player.currentMediaItem?.let { analyticsBridge.onTrackStart(it, player.currentPosition) }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        if (config.playbackSetting != PlaybackSetting.STOP_AFTER_CURRENT_TRACK) {
            config.playbackSetting = getPlaybackSetting(repeatMode)
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        config.isShuffleEnabled = shuffleModeEnabled
    }
}
