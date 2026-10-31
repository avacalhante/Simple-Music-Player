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
