package com.simplemobiletools.musicplayer.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.rescanPaths
import com.simplemobiletools.commons.extensions.sharePathsIntent
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.dialogs.SelectPlaylistDialog
import com.simplemobiletools.musicplayer.helpers.FLAG_MANUAL_CACHE
import com.simplemobiletools.musicplayer.helpers.PlaylistExporter
import com.simplemobiletools.musicplayer.helpers.RoomHelper
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track

fun Activity.addTracksToPlaylist(tracks: List<Track>, callback: () -> Unit) {
    SelectPlaylistDialog(this) { playlistId ->
        val tracksToAdd = ArrayList<Track>()
        tracks.forEach {
            it.id = 0
            it.playListId = playlistId
            tracksToAdd.add(it)
        }

        ensureBackgroundThread {
            RoomHelper(this).insertTracksWithPlaylist(tracksToAdd)

            runOnUiThread {
                callback()
            }
        }
    }
}

fun Activity.maybeRescanTrackPaths(tracks: List<Track>, callback: (tracks: List<Track>) -> Unit) {
    val tracksWithoutId = tracks.filter { it.mediaStoreId == 0L || (it.flags and FLAG_MANUAL_CACHE != 0) }
    if (tracksWithoutId.isNotEmpty()) {
        val pathsToRescan = tracksWithoutId.map { it.path }
        rescanPaths(pathsToRescan) {
            for (track in tracks) {
                if (track.mediaStoreId == 0L || (track.flags and FLAG_MANUAL_CACHE != 0)) {
                    track.mediaStoreId = getMediaStoreIdFromPath(track.path)
                }
            }

            callback(tracks)
        }
    } else {
        callback(tracks)
    }
}

/**
 * Gathers the tracks of the given playlists and lets the user pick the folder they are exported into.
 * The files are written by [PlaylistExporter] once the folder picker returns to the activity.
 */
fun Activity.exportPlaylists(playlists: List<Playlist>) {
    ensureBackgroundThread {
        val entries = playlists.mapNotNull { playlist ->
            val tracks = audioHelper.getPlaylistTracks(playlist.id)
            if (tracks.isEmpty()) null else PlaylistExporter.ExportEntry(playlist.title, tracks)
        }

        runOnUiThread {
            if (entries.isEmpty()) {
                toast(com.simplemobiletools.commons.R.string.no_entries_for_exporting)
                return@runOnUiThread
            }

            PlaylistExporter.prepare(entries)
            try {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PlaylistExporter.REQUEST_CODE)
            } catch (e: ActivityNotFoundException) {
                PlaylistExporter.forget()
                toast(com.simplemobiletools.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
            }
        }
    }
}

fun Activity.showTrackProperties(selectedTracks: List<Track>) {
    val selectedPaths = selectedTracks.map { track ->
        track.path.ifEmpty {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId).toString()
        }
    }

    if (selectedPaths.size <= 1) {
        PropertiesDialog(this, selectedPaths.first(), false)
    } else {
        PropertiesDialog(this, selectedPaths, false)
    }
}

fun Activity.ensureActivityNotDestroyed(callback: () -> Unit) {
    if (!isFinishing && !isDestroyed) {
        callback()
    }
}

fun Activity.shareFiles(tracks: List<Track>) {
    val paths = tracks.map { it.path }
    sharePathsIntent(paths, BuildConfig.APPLICATION_ID)
}
