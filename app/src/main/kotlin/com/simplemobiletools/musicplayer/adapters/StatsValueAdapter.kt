package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.musicplayer.databinding.ItemStatsValueBinding

data class StatsValueRow(val label: String, val value: String)

class StatsValueAdapter(private val items: List<StatsValueRow>) : RecyclerView.Adapter<StatsValueAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemStatsValueBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStatsValueBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            statsValueLabel.text = item.label
            statsValueText.text = item.value
        }
    }
}
