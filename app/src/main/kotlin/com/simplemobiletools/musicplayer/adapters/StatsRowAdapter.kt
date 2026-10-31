package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.musicplayer.databinding.ItemStatsRowBinding

data class StatsRow(val title: String, val playCount: Int)

class StatsRowAdapter(private val items: List<StatsRow>) : RecyclerView.Adapter<StatsRowAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStatsRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStatsRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            statsRowTitle.text = item.title
            statsRowCount.text = root.resources.getQuantityString(
                com.simplemobiletools.musicplayer.R.plurals.play_count,
                item.playCount,
                item.playCount
            )
        }
    }
}
