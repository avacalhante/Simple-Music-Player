package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.DailyListeningAdapter
import com.simplemobiletools.musicplayer.adapters.MostPlayedRow
import com.simplemobiletools.musicplayer.adapters.MostPlayedTracksAdapter
import com.simplemobiletools.musicplayer.adapters.StatsRow
import com.simplemobiletools.musicplayer.adapters.StatsRowAdapter
import com.simplemobiletools.musicplayer.adapters.StatsTrackAdapter
import com.simplemobiletools.musicplayer.adapters.StatsTrackRow
import com.simplemobiletools.musicplayer.adapters.StatsValueAdapter
import com.simplemobiletools.musicplayer.adapters.StatsValueRow
import com.simplemobiletools.musicplayer.analytics.ListeningAnalyticsStore
import com.simplemobiletools.musicplayer.analytics.daos.DailyStats
import com.simplemobiletools.musicplayer.analytics.daos.SessionInfo
import com.simplemobiletools.musicplayer.analytics.daos.WeekdaySessionWindow
import com.simplemobiletools.musicplayer.analytics.extensions.analytics
import com.simplemobiletools.musicplayer.databinding.ActivityStatsBinding
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Track
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

private const val MOST_PLAYED_LIMIT = 20
private const val AGGREGATE_LIMIT = 500
private const val GROUP_LIMIT = 5
private const val DAYS_LIMIT = 14
private const val RESOLVE_CHUNK_SIZE = 400
private const val RECENT_SESSIONS_LIMIT = 5
private const val MINUTES_PER_DAY = 24 * 60

class StatsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityStatsBinding::inflate)
    private val lastPlayedFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private val sessionDateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.statsCoordinator, binding.statsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.statsNestedScrollview, binding.statsToolbar)

        arrayOf(
            binding.statsSummaryList, binding.statsMostPlayedList, binding.statsArtistsList, binding.statsAlbumsList,
            binding.statsGenresList, binding.statsWeekdayHoursList, binding.statsWeekdaysList, binding.statsForgottenList, binding.statsDailyList,
            binding.statsSessionsList, binding.statsRecentSessionsList
        ).forEach {
            it.layoutManager = LinearLayoutManager(this)
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.statsToolbar, NavigationIcon.Arrow)
        arrayOf(
            binding.statsSummaryLabel, binding.statsMostPlayedLabel, binding.statsArtistsLabel, binding.statsAlbumsLabel,
            binding.statsGenresLabel, binding.statsWeekdayHoursLabel, binding.statsWeekdaysLabel, binding.statsForgottenLabel, binding.statsDailyLabel,
            binding.statsSessionsLabel, binding.statsRecentSessionsLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
        loadStats()
    }

    private fun loadStats() {
        ensureBackgroundThread {
            val analytics = applicationContext.analytics
            val mostPlayed = analytics.getMostPlayedTracks(AGGREGATE_LIMIT)
            val playedTracks = resolveTracks(mostPlayed.map { it.trackId })

            // plays of tracks that are no longer in the library are skipped
            val playedRows = mostPlayed.mapNotNull { played ->
                playedTracks[played.trackId]?.let { MostPlayedRow(it, played.playCount) }
            }

            val stalePlays = analytics.getTracksNotPlayedSince()
            val staleTracks = resolveTracks(stalePlays.map { it.trackId })
            val forgottenRows = stalePlays.mapNotNull { stale ->
                staleTracks[stale.trackId]?.let { StatsTrackRow(it, lastPlayedFormat.format(stale.lastPlayedAt * 1000L)) }
            }

            val summaryRows = buildSummary(analytics)
            val artistRows = groupBy(playedRows) { it.track.artist }
            val albumRows = groupBy(playedRows) { it.track.album }
            val genreRows = groupBy(playedRows) { it.track.genre }
            val weekdayHourRows = analytics.getWeekdaySessionWindows().map { it.toTimeRangeRow() }
            val weekdayRows = analytics.getDayOfWeekStats().map { StatsRow(weekdayName(it.dayOfWeek), it.playCount) }
            val dailyStats: List<DailyStats> = analytics.getDailyStats(DAYS_LIMIT)
            val sessions = analytics.getSessionInfo()
            val sessionRows = buildSessionSummary(sessions)
            val recentSessionRows = sessions.take(RECENT_SESSIONS_LIMIT).map { session ->
                StatsValueRow(
                    sessionDateFormat.format(session.startedAt * 1000L),
                    "${session.lengthSeconds.getFormattedDuration()} · ${resources.getQuantityString(R.plurals.tracks_plural, session.trackCount, session.trackCount)}"
                )
            }

            runOnUiThread {
                binding.statsSummaryList.adapter = StatsValueAdapter(summaryRows)
                binding.statsMostPlayedList.adapter = MostPlayedTracksAdapter(this, playedRows.take(MOST_PLAYED_LIMIT))
                binding.statsArtistsList.adapter = StatsRowAdapter(artistRows)
                binding.statsAlbumsList.adapter = StatsRowAdapter(albumRows)
                binding.statsGenresList.adapter = StatsRowAdapter(genreRows)
                binding.statsWeekdayHoursList.adapter = StatsValueAdapter(weekdayHourRows)
                binding.statsWeekdaysList.adapter = StatsRowAdapter(weekdayRows)
                binding.statsForgottenList.adapter = StatsTrackAdapter(forgottenRows)
                binding.statsDailyList.adapter = DailyListeningAdapter(this, dailyStats)
                binding.statsSessionsList.adapter = StatsValueAdapter(sessionRows)
                binding.statsRecentSessionsList.adapter = StatsValueAdapter(recentSessionRows)

                binding.statsMostPlayedEmpty.beVisibleIf(playedRows.isEmpty())
                binding.statsDailyEmpty.beVisibleIf(dailyStats.isEmpty())

                binding.statsArtistsSection.beVisibleIf(artistRows.isNotEmpty())
                binding.statsAlbumsSection.beVisibleIf(albumRows.isNotEmpty())
                binding.statsGenresSection.beVisibleIf(genreRows.isNotEmpty())
                binding.statsWeekdayHoursSection.beVisibleIf(weekdayHourRows.isNotEmpty())
                binding.statsWeekdaysSection.beVisibleIf(weekdayRows.isNotEmpty())
                binding.statsForgottenSection.beVisibleIf(playedRows.isNotEmpty())
                binding.statsSessionsSection.beVisibleIf(sessionRows.isNotEmpty())
                binding.statsRecentSessionsSection.beVisibleIf(recentSessionRows.isNotEmpty())
                binding.statsForgottenEmpty.beVisibleIf(forgottenRows.isEmpty())

                updateTextColors(binding.statsNestedScrollview)
            }
        }
    }

    private fun buildSummary(analytics: ListeningAnalyticsStore): List<StatsValueRow> {
        val engagement = analytics.getEngagementCounts()
        val libraryTrackCount = tracksDAO.getDistinctTracksCount()
        val playedTrackCount = analytics.distinctTrackCount()

        val rows = arrayListOf(
            StatsValueRow(getString(R.string.total_plays), analytics.totalPlayCount().toString()),
            StatsValueRow(getString(R.string.total_listening_time), analytics.totalListeningSeconds().toInt().getFormattedDuration())
        )

        if (engagement.endCount > 0) {
            // skipped is the complement of fully played, so round once and derive the other
            val finished = engagement.fullyPlayedCount * 100 / engagement.endCount
            rows.add(StatsValueRow(getString(R.string.completion_rate), "$finished%"))
            rows.add(StatsValueRow(getString(R.string.skip_rate), "${100 - finished}%"))
        }

        if (libraryTrackCount > 0) {
            val neverPlayed = (libraryTrackCount - playedTrackCount).coerceAtLeast(0)
            rows.add(StatsValueRow(getString(R.string.never_played), getString(R.string.never_played_summary, neverPlayed, libraryTrackCount)))
        }

        return rows
    }

    private fun buildSessionSummary(sessions: List<SessionInfo>): List<StatsValueRow> {
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val averageLength = sessions.sumOf { it.lengthSeconds } / sessions.size
        val longestLength = sessions.maxOf { it.lengthSeconds }
        val averageTracks = sessions.sumOf { it.trackCount }.toDouble() / sessions.size
        val singleSongSessions = sessions.count { it.trackCount == 1 }
        val singleSongRate = singleSongSessions * 100 / sessions.size

        return arrayListOf(
            StatsValueRow(getString(R.string.session_count), sessions.size.toString()),
            StatsValueRow(getString(R.string.average_session_length), averageLength.getFormattedDuration()),
            StatsValueRow(getString(R.string.longest_session), longestLength.getFormattedDuration()),
            StatsValueRow(getString(R.string.average_tracks_per_session), String.format(Locale.getDefault(), "%.1f", averageTracks)),
            StatsValueRow(getString(R.string.single_song_sessions), "$singleSongRate%")
        )
    }

    private fun resolveTracks(mediaStoreIds: List<Long>): Map<Long, Track> {
        return mediaStoreIds.chunked(RESOLVE_CHUNK_SIZE)
            .flatMap { tracksDAO.getTracksWithMediaStoreIds(it) }
            .groupBy { it.mediaStoreId }
            .mapValues { it.value.first() }
    }

    private fun groupBy(rows: List<MostPlayedRow>, key: (MostPlayedRow) -> String): List<StatsRow> {
        return rows.filter { key(it).isNotBlank() }
            .groupBy { key(it) }
            .map { (name, plays) -> StatsRow(name, plays.sumOf { it.playCount }) }
            .sortedByDescending { it.playCount }
            .take(GROUP_LIMIT)
    }

    /** "Saturday" + "12:00 – 14:00", the mean session start and the mean session end of that weekday. */
    private fun WeekdaySessionWindow.toTimeRangeRow(): StatsValueRow {
        val startMinutes = startMinutes.roundToInt()
        val endMinutes = startMinutes + (lengthSeconds / 60).roundToInt()
        return StatsValueRow(weekdayName(dayOfWeek), getString(R.string.time_range, formatMinuteOfDay(startMinutes), formatMinuteOfDay(endMinutes)))
    }

    /** A session ending after midnight wraps around, so a 23:30 start with 2 hours shows 01:30. */
    private fun formatMinuteOfDay(minutesOfDay: Int): String {
        val normalized = ((minutesOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return String.format(Locale.getDefault(), "%02d:%02d", normalized / 60, normalized % 60)
    }

    private fun weekdayName(dayOfWeek: Int): String = DateFormatSymbols.getInstance().weekdays[dayOfWeek + 1]
}
