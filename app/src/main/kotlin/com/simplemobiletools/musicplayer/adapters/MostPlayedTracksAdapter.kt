package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemStatsTrackBinding
import com.simplemobiletools.musicplayer.models.Track

data class MostPlayedRow(val track: Track, val playCount: Int)

class MostPlayedTracksAdapter(
    private val activity: BaseSimpleActivity,
    private val items: List<MostPlayedRow>
) : RecyclerView.Adapter<MostPlayedTracksAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStatsTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStatsTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            statsTrackRank.text = (position + 1).toString()
            statsTrackTitle.text = item.track.title
            statsTrackInfo.text = "${item.track.artist} • ${item.track.album}"
            statsTrackPlayCount.text = activity.resources.getQuantityString(R.plurals.play_count, item.playCount, item.playCount)
        }
    }
}
