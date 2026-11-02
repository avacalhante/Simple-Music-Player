package com.simplemobiletools.musicplayer.helpers

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.writeLn
import com.simplemobiletools.musicplayer.models.Track
import java.io.OutputStream

class M3uExporter(val activity: BaseSimpleActivity) {
    var failedEvents = 0
    var exportedEvents = 0

    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    fun exportPlaylist(
        outputStream: OutputStream?,
        tracks: ArrayList<Track>,
        callback: (result: ExportResult) -> Unit
    ) {
        if (outputStream == null) {
            callback(ExportResult.EXPORT_FAIL)
            return
        }

        activity.toast(com.simplemobiletools.commons.R.string.exporting)

        try {
            writeTo(outputStream, tracks) { exportedEvents++ }
        } catch (e: Exception) {
            failedEvents++
            activity.showErrorToast(e)
        } finally {
            outputStream.close()
        }

        callback(
            when {
                exportedEvents == 0 -> ExportResult.EXPORT_FAIL
                failedEvents > 0 -> ExportResult.EXPORT_PARTIAL
                else -> ExportResult.EXPORT_OK
            }
        )
    }

    companion object {
        /** Writes the m3u content, shared with the multi-playlist export which owns its own destination file. */
        fun writeTo(outputStream: OutputStream, tracks: List<Track>, onTrackWritten: () -> Unit = {}) {
            outputStream.bufferedWriter().use { out ->
                out.writeLn(M3U_HEADER)
                for (track in tracks) {
                    out.writeLn(M3U_ENTRY + track.duration + M3U_DURATION_SEPARATOR + track.artist + " - " + track.title)
                    out.writeLn(track.path)
                    onTrackWritten()
                }
            }
        }
    }
}
