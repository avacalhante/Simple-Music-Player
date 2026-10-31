package com.simplemobiletools.musicplayer.analytics

import com.simplemobiletools.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID

/**
 * Interprets the current browse location of the playback session for analytics purposes.
 * It mirrors the root ids used by [com.simplemobiletools.musicplayer.playback.library.MediaItemProvider]
 * and degrades gracefully when the context is unknown.
 */
object SessionContext {
    /**
     * The playlist a play belongs to, or 0 when it cannot be attributed.
     * Per-playlist attribution is not tracked yet, so only the all-tracks root is resolved.
     */
    fun playlistIdFor(rootId: String?): Int {
        return if (rootId == MediaItemProviderRootIds.TRACKS_ROOT) ALL_TRACKS_PLAYLIST_ID else 0
    }

    fun sourceFor(rootId: String?): String {
        return when (rootId) {
            MediaItemProviderRootIds.PLAYLISTS_ROOT -> "playlist"
            MediaItemProviderRootIds.FOLDERS_ROOT -> "folder"
            MediaItemProviderRootIds.ARTISTS_ROOT -> "artist"
            MediaItemProviderRootIds.ALBUMS_ROOT -> "album"
            MediaItemProviderRootIds.GENRES_ROOT -> "genre"
            MediaItemProviderRootIds.TRACKS_ROOT -> "all_tracks"
            else -> "unknown"
        }
    }
}

/**
 * Mirrors the root ids used by [com.simplemobiletools.musicplayer.playback.library.MediaItemProvider]
 * so analytics can interpret the current root without depending on that class.
 */
object MediaItemProviderRootIds {
    const val PLAYLISTS_ROOT = "__PLAYLISTS__"
    const val FOLDERS_ROOT = "__FOLDERS__"
    const val ARTISTS_ROOT = "__ARTISTS__"
    const val ALBUMS_ROOT = "__ALBUMS__"
    const val TRACKS_ROOT = "__TRACKS__"
    const val GENRES_ROOT = "__GENRES__"
}
