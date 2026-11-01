package com.simplemobiletools.musicplayer.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.provider.MediaStore
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ViewCurrentTrackBarBinding
import com.simplemobiletools.musicplayer.extensions.*

class CurrentTrackBar(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    private val binding by viewBinding(ViewCurrentTrackBarBinding::bind)

    // Cover art loads asynchronously, so a load started for an earlier track can finish after the
    // current one and briefly show the wrong cover. Each request gets an id and stale results are dropped.
    private var artworkRequestId = 0

    fun initialize(togglePlayback: () -> Unit) {
        binding.currentTrackPlayPause.setOnClickListener {
            togglePlayback()
        }
    }

    fun updateColors() {
        background = ColorDrawable(context.getProperBackgroundColor())
        binding.currentTrackLabel.setTextColor(context.getProperTextColor())
    }

    fun updateCurrentTrack(mediaItem: MediaItem?) {
        val track = mediaItem?.toTrack()
        if (track == null) {
            artworkRequestId++
            fadeOut()
            return
        } else {
            fadeIn()
        }

        val artist = if (track.artist.trim().isNotEmpty() && track.artist != MediaStore.UNKNOWN_STRING) {
            " • ${track.artist}"
        } else {
            ""
        }

        @SuppressLint("SetTextI18n")
        binding.currentTrackLabel.text = "${track.title}$artist"
        val cornerRadius = resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_small).toInt()
        val currentTrackPlaceholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, context.getProperTextColor())
        val options = RequestOptions()
            .error(currentTrackPlaceholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        val requestId = ++artworkRequestId
        context.getTrackCoverArt(track) { coverArt ->
            (context as? Activity)?.ensureActivityNotDestroyed {
                if (requestId == artworkRequestId) {
                    Glide.with(this)
                        .load(coverArt)
                        .apply(options)
                        .into(findViewById(R.id.current_track_image))
                }
            }
        }
    }

    fun updateTrackState(isPlaying: Boolean) {
        binding.currentTrackPlayPause.updatePlayPauseIcon(isPlaying, context.getProperTextColor())
    }
}
