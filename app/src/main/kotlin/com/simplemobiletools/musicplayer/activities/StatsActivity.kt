package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.adapters.DailyListeningAdapter
import com.simplemobiletools.musicplayer.adapters.MostPlayedRow
import com.simplemobiletools.musicplayer.adapters.MostPlayedTracksAdapter
import com.simplemobiletools.musicplayer.analytics.daos.DailyStats
import com.simplemobiletools.musicplayer.analytics.extensions.analytics
import com.simplemobiletools.musicplayer.databinding.ActivityStatsBinding
import com.simplemobiletools.musicplayer.extensions.tracksDAO

private const val MOST_PLAYED_LIMIT = 20
private const val DAYS_LIMIT = 14

class StatsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityStatsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.statsCoordinator, binding.statsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.statsNestedScrollview, binding.statsToolbar)

        arrayOf(binding.statsMostPlayedList, binding.statsDailyList).forEach {
            it.layoutManager = LinearLayoutManager(this)
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.statsToolbar, NavigationIcon.Arrow)
        binding.statsMostPlayedLabel.setTextColor(getProperPrimaryColor())
        binding.statsDailyLabel.setTextColor(getProperPrimaryColor())
        loadStats()
    }

    private fun loadStats() {
        ensureBackgroundThread {
            val analytics = applicationContext.analytics
            val mostPlayed = analytics.getMostPlayedTracks(MOST_PLAYED_LIMIT)
            val tracksByMediaStoreId = if (mostPlayed.isEmpty()) {
                emptyMap()
            } else {
                tracksDAO.getTracksWithMediaStoreIds(mostPlayed.map { it.trackId })
                    .groupBy { it.mediaStoreId }
                    .mapValues { it.value.first() }
            }

            // plays of tracks that are no longer in the library are skipped
            val mostPlayedRows = mostPlayed.mapNotNull { played ->
                tracksByMediaStoreId[played.trackId]?.let { MostPlayedRow(it, played.playCount) }
            }
            val dailyStats: List<DailyStats> = analytics.getDailyStats(DAYS_LIMIT)

            runOnUiThread {
                binding.statsMostPlayedList.adapter = MostPlayedTracksAdapter(this, mostPlayedRows)
                binding.statsDailyList.adapter = DailyListeningAdapter(this, dailyStats)
                binding.statsMostPlayedEmpty.beVisibleIf(mostPlayedRows.isEmpty())
                binding.statsDailyEmpty.beVisibleIf(dailyStats.isEmpty())
                updateTextColors(binding.statsNestedScrollview)
            }
        }
    }
}
