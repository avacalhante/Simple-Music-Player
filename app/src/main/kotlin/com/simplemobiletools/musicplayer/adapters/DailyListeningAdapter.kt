package com.simplemobiletools.musicplayer.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.analytics.daos.DailyStats
import com.simplemobiletools.musicplayer.databinding.ItemStatsDayBinding
import java.text.SimpleDateFormat
import java.util.Locale

class DailyListeningAdapter(
    private val activity: BaseSimpleActivity,
    private val items: List<DailyStats>
) : RecyclerView.Adapter<DailyListeningAdapter.ViewHolder>() {

    private val storedDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDayFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    inner class ViewHolder(val binding: ItemStatsDayBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemStatsDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            statsDayLabel.text = formatDay(item.day)
            statsDayPlayCount.text = activity.resources.getQuantityString(R.plurals.play_count, item.playCount, item.playCount)
            statsDayDuration.text = item.playedSeconds.toInt().getFormattedDuration()
        }
    }

    private fun formatDay(day: String): String {
        val parsed = try {
            storedDayFormat.parse(day)
        } catch (e: Exception) {
            null
        }
        return parsed?.let { displayDayFormat.format(it) } ?: day
    }
}
