package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.musicplayer.databinding.ItemStatsTrackBinding
import com.simplemobiletools.musicplayer.models.Track

data class StatsTrackRow(val track: Track, val trailing: String)

class StatsTrackAdapter(private val items: List<StatsTrackRow>) : RecyclerView.Adapter<StatsTrackAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStatsTrackBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStatsTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            statsTrackRank.beGone()
            statsTrackTitle.text = item.track.title
            statsTrackInfo.text = "${item.track.artist} • ${item.track.album}"
            statsTrackPlayCount.text = item.trailing
        }
    }
}
