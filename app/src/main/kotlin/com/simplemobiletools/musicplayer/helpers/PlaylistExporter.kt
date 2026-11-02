package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplemobiletools.musicplayer.models.Track

/**
 * Exports several playlists at once, one .m3u per playlist, into a folder picked through the Storage Access
 * Framework. The playlists are prepared when the export is requested and held until the folder picker returns,
 * since the picker result comes back to the activity that started it.
 */
object PlaylistExporter {
    const val REQUEST_CODE = 3

    data class ExportEntry(val title: String, val tracks: List<Track>)

    @Volatile
    private var pending: List<ExportEntry> = emptyList()

    fun prepare(entries: List<ExportEntry>) {
        pending = entries
    }

    fun forget() {
        pending = emptyList()
    }

    /**
     * Writes the prepared playlists into [treeUri] and returns how many files were written.
     * A file left behind by an earlier export of the same playlist is replaced.
     */
    fun write(context: Context, treeUri: Uri): Int {
        val entries = pending
        pending = emptyList()
        val folder = DocumentFile.fromTreeUri(context, treeUri) ?: return 0

        var exported = 0
        val usedNames = HashSet<String>()
        for (entry in entries) {
            val fileName = getFileName(entry.title, usedNames)
            folder.findFile(fileName)?.delete()

            val file = folder.createFile(MIME_TYPE_M3U, fileName) ?: continue
            try {
                context.contentResolver.openOutputStream(file.uri)?.use { M3uExporter.writeTo(it, entry.tracks) }
                exported++
            } catch (e: Exception) {
                file.delete()
            }
        }

        return exported
    }

    /** Playlist titles are user input, so strip what Android does not allow in a file name and keep names unique. */
    private fun getFileName(title: String, usedNames: HashSet<String>): String {
        val base = title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { DEFAULT_FILE_NAME }
        var name = "$base.m3u"
        var counter = 2
        while (!usedNames.add(name)) {
            name = "$base ($counter).m3u"
            counter++
        }

        return name
    }

    private const val DEFAULT_FILE_NAME = "playlist"
}
