package com.simplemobiletools.musicplayer.analytics

import com.simplemobiletools.musicplayer.analytics.entities.ListeningSession
import com.simplemobiletools.musicplayer.analytics.entities.PlayEnd
import com.simplemobiletools.musicplayer.analytics.entities.PlayStart

/**
 * JSON snapshot of the analytics tables, used to back the listening history up and to move it between installs.
 *
 * The tables are stored with their ids so the restored plays keep pointing at their session and its end,
 * which is why an import replaces the whole history instead of merging it: ids of two histories would collide.
 *
 * The collections are nullable because Gson skips the constructor defaults for fields missing in the file,
 * so garbage input still parses and is then rejected by [version] instead of throwing halfway through.
 */
data class AnalyticsBackup(
    val version: Int = 0,
    val exportedAt: Int = 0,
    val playStarts: List<PlayStart>? = null,
    val playEnds: List<PlayEnd>? = null,
    val sessions: List<ListeningSession>? = null
) {
    companion object {
        const val VERSION = 1
    }
}
