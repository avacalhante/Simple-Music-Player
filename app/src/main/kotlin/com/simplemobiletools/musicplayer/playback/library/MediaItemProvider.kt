package com.simplemobiletools.musicplayer.playback.library

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaMetadata.MediaType
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.MoreExecutors
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.TAB_ALBUMS
import com.simplemobiletools.musicplayer.helpers.TAB_ARTISTS
import com.simplemobiletools.musicplayer.helpers.TAB_FOLDERS
import com.simplemobiletools.musicplayer.helpers.TAB_GENRES
import com.simplemobiletools.musicplayer.helpers.TAB_PLAYLISTS
import com.simplemobiletools.musicplayer.helpers.TAB_TRACKS
import com.simplemobiletools.musicplayer.models.*
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

private const val STATE_CREATED = 1
private const val STATE_INITIALIZING = 2
private const val STATE_INITIALIZED = 3
private const val STATE_ERROR = 4

// SQLite allows at most 999 variables per statement
private const val MEDIA_STORE_ID_CHUNK = 900

private const val SMP_ROOT_ID = "__ROOT__"
private const val SMP_PLAYLISTS_ROOT_ID = "__PLAYLISTS__"
private const val SMP_FOLDERS_ROOT_ID = "__FOLDERS__"
private const val SMP_ARTISTS_ROOT_ID = "__ARTISTS__"
private const val SMP_ALBUMS_ROOT_ID = "__ALBUMS__"
private const val SMP_TRACKS_ROOT_ID = "__TRACKS__"
private const val SMP_GENRES_ROOT_ID = "__GENRES__"

/**
 * This is not (yet) used internally and currently only required (mostly) for media browser's outside the app.
 *
 * The tree is built lazily on the first browser request and rebuilt from a single set of queries,
 * since the previous implementation ran one query per album/artist/folder/genre/playlist.
 */
@UnstableApi
internal class MediaItemProvider(private val context: Context) {
    private val treeExecutor by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private val persistenceExecutor by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private val audioHelper = context.audioHelper
    private val lock = Any()

    @Volatile
    private var state: Int = STATE_CREATED
    private var rebuildRequested = false
    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()

    @Volatile
    private var treeNodes: Map<String, MediaItemNode> = emptyMap()

    @Volatile
    private var titleMap: Map<String, MediaItem> = emptyMap()

    // ids of the queue as it was last written to the database, used to skip needless rewrites
    private var savedQueueIds: List<Long>? = null

    init {
        // the root and its category nodes do not need any database access, so they are always available
        treeNodes = TreeBuilder(buildRootItem(), RootCategories.buildRootChildren(context)).nodes
    }

    /**
     * Starts building the tree unless it is already being built or has been built.
     */
    fun ensureBuilt() {
        synchronized(lock) {
            if (state == STATE_CREATED) {
                startBuildLocked()
            }
        }
    }

    fun whenReady(performAction: (Boolean) -> Unit): Boolean {
        var completedState: Int? = null
        synchronized(lock) {
            when (state) {
                STATE_CREATED -> {
                    startBuildLocked()
                    onReadyListeners += performAction
                }

                STATE_INITIALIZING -> onReadyListeners += performAction
                else -> completedState = state
            }
        }

        completedState?.let {
            performAction(it == STATE_INITIALIZED)
            return true
        }

        return false
    }

    operator fun get(mediaId: String): MediaItem? {
        val mediaItem = getNode(mediaId)?.item
        if (mediaItem == null) {
            // assume it's a track
            val mediaStoreId = mediaId.toLongOrNull() ?: return null
            return audioHelper.getTrack(mediaStoreId)?.toMediaItem()
        }

        return mediaItem
    }

    /**
     * Returns an already built item without touching the database.
     */
    fun getCachedItem(mediaId: String) = getNode(mediaId)?.item

    /**
     * Resolves tracks that are not in the tree yet, using a few chunked queries instead of one per id.
     */
    fun getItemsByIds(mediaStoreIds: Collection<Long>): Map<Long, MediaItem> {
        if (mediaStoreIds.isEmpty()) {
            return emptyMap()
        }

        return mediaStoreIds.distinct()
            .chunked(MEDIA_STORE_ID_CHUNK)
            .flatMap { context.tracksDAO.getTracksWithMediaStoreIds(it) }
            .associateBy { it.mediaStoreId }
            .mapValues { (_, track) -> track.toMediaItem() }
    }

    fun getRootItem() = get(SMP_ROOT_ID)!!

    fun getChildren(id: String) = getNode(id)?.getChildren()

    fun getRandomItem(): MediaItem {
        if (state != STATE_INITIALIZED) {
            // do not wait for the tree, the database has the same information
            context.tracksDAO.getRandomTrack()?.let { return it.toMediaItem() }
        }

        var curRoot = getRootItem()
        while (curRoot.mediaMetadata.isBrowsable == true) {
            val children = getChildren(curRoot.mediaId).orEmpty()
            if (children.isEmpty()) {
                break
            }
            curRoot = children.random()
        }

        return curRoot
    }

    fun getItemFromSearch(searchQuery: String): MediaItem? {
        var mediaItem = titleMap[searchQuery]
        if (mediaItem == null) {
            val partialMatch = titleMap.keys.find { it.contains(searchQuery) }
            if (partialMatch != null) {
                mediaItem = titleMap[partialMatch]
            }
        }

        if (mediaItem != null) {
            return mediaItem
        }

        // the tree may not be built yet, or the track may have been added since
        return (context.tracksDAO.getTrackWithTitle(searchQuery)
            ?: context.tracksDAO.getTrackWithTitleContaining(searchQuery))?.toMediaItem()
    }

    fun getDefaultQueue() = getChildren(SMP_TRACKS_ROOT_ID)

    fun getRecentItemsLazily(callback: (itemsWithStartPosition: MediaItemsWithStartPosition) -> Unit) {
        if (state == STATE_INITIALIZED) {
            callback(getRecentItemsWithStartPosition())
        } else {
            audioHelper.getQueuedTracksLazily { tracks, startIndex, startPositionMs ->
                callback(MediaItemsWithStartPosition(tracks.toMediaItems(), startIndex, startPositionMs))
            }
        }
    }

    fun getRecentItemsWithStartPosition(): MediaItemsWithStartPosition {
        val recentItems = context.queueDAO.getAll().mapNotNull { getMediaItemFromQueueItem(it) }
        var startPosition = 0L
        val currentItem = context.queueDAO.getCurrent()?.let {
            val mediaItem = getMediaItemFromQueueItem(it) ?: return@let null
            val metadata = mediaItem.mediaMetadata.buildUpon().build()
            startPosition = it.lastPosition * 1000L
            mediaItem.buildUpon().setMediaMetadata(metadata).build()
        }

        val startIndex = maxOf(recentItems.indexOf(currentItem), 0)
        return MediaItemsWithStartPosition(recentItems, startIndex, startPosition)
    }

    fun saveRecentItemsWithStartPosition(mediaItems: List<MediaItem>, current: MediaItem, startPosition: Long) {
        if (mediaItems.isEmpty()) {
            return
        }

        persistenceExecutor.execute {
            val trackIds = mediaItems.map { it.mediaId.toLong() }
            val currentTrackId = current.mediaId.toLong()
            val positionSeconds = startPosition.coerceAtLeast(0L).milliseconds.inWholeSeconds.toInt()
            val previousIds = savedQueueIds ?: context.queueDAO.getAll().map { it.trackId }
            savedQueueIds = previousIds

            if (trackIds == previousIds) {
                // the player also reports timeline changes for internal reasons such as the shuffle order,
                // so only touch the current row when the queue itself did not change
                context.queueDAO.updateCurrentTrack(currentTrackId, positionSeconds)
                return@execute
            }

            val queueItems = trackIds.mapIndexed { index, trackId ->
                QueueItem(trackId = trackId, trackOrder = index, isCurrent = false, lastPosition = 0)
            }

            audioHelper.resetQueue(queueItems, currentTrackId, startPosition)
            savedQueueIds = trackIds
        }
    }

    /**
     * Persists only the current track and its position, for playback events that do not change the queue.
     */
    fun saveCurrentTrackProgress(trackId: Long, startPosition: Long) {
        persistenceExecutor.execute {
            val positionSeconds = startPosition.coerceAtLeast(0L).milliseconds.inWholeSeconds.toInt()
            context.queueDAO.updateCurrentTrack(trackId, positionSeconds)
        }
    }

    /**
     * Invalidates the tree. It is rebuilt lazily on the next browser request, unless a build is already running,
     * in which case a rebuild is queued after it.
     */
    fun reload() {
        synchronized(lock) {
            when (state) {
                STATE_INITIALIZING -> rebuildRequested = true
                STATE_INITIALIZED, STATE_ERROR -> state = STATE_CREATED
            }
        }
    }

    private fun startBuildLocked() {
        state = STATE_INITIALIZING
        treeExecutor.execute { build() }
    }

    private fun build() {
        val builtTree = try {
            buildTree()
        } catch (e: Exception) {
            null
        }

        if (builtTree != null) {
            treeNodes = builtTree.nodes
            titleMap = builtTree.titleMap
        }

        val listeners: List<(Boolean) -> Unit>
        val rebuild: Boolean
        synchronized(lock) {
            if (rebuildRequested) {
                rebuildRequested = false
                rebuild = true
                listeners = emptyList()
            } else {
                rebuild = false
                state = if (builtTree != null) STATE_INITIALIZED else STATE_ERROR
                listeners = onReadyListeners.toList()
                onReadyListeners.clear()
            }
        }

        if (rebuild) {
            // still on the single build thread, so recursing here avoids a second executor task
            build()
            return
        }

        if (listeners.isNotEmpty()) {
            val success = builtTree != null
            Handler(Looper.getMainLooper()).post {
                listeners.forEach { listener -> listener(success) }
            }
        }
    }

    private fun buildTree(): BuiltTree {
        val libraryTracks = audioHelper.getLibraryTracks()
        val playlists = audioHelper.getAllPlaylists()
        val folders = audioHelper.getAllFolders(libraryTracks.all)
        val albums = audioHelper.getAllAlbums()
        val artists = audioHelper.getAllArtists()
        val genres = audioHelper.getAllGenres()

        val tracksByAlbum = libraryTracks.all.groupBy { it.albumId }
        val tracksByGenre = libraryTracks.all.groupBy { it.genreId }
        val tracksByFolder = libraryTracks.all.groupBy { it.folderName.lowercase() }
        val albumsByArtist = albums.groupBy { it.artistId }

        val tree = TreeBuilder(buildRootItem(), RootCategories.buildRootChildren(context))
        val trackItems = HashMap<Long, MediaItem>()

        fun trackItem(track: Track): MediaItem = trackItems.getOrPut(track.mediaStoreId) { track.toMediaItem() }

        playlists.forEach { playlist ->
            val tracks = ArrayList(libraryTracks.byPlaylist[playlist.id].orEmpty())
            tracks.sortSafely(context.config.getProperPlaylistSorting(playlist.id))
            val item = playlist.copy(trackCount = tracks.size).toMediaItem()
            tree.add(item, SMP_PLAYLISTS_ROOT_ID)
            tree.addAll(tracks.map { trackItem(it) }, item.mediaId)
        }

        folders.forEach { folder ->
            val tracks = ArrayList(tracksByFolder[folder.title.lowercase()].orEmpty())
            tracks.sortSafely(context.config.getProperFolderSorting(folder.title))
            val item = folder.toMediaItem()
            tree.add(item, SMP_FOLDERS_ROOT_ID)
            tree.addAll(tracks.map { trackItem(it) }, item.mediaId)
        }

        val albumItems = albums.associate { it.id to it.toMediaItem() }

        artists.forEach { artist ->
            val artistAlbums = albumsByArtist[artist.id].orEmpty().mapNotNull { albumItems[it.id] }
            val item = artist.toMediaItem()
            tree.add(item, SMP_ARTISTS_ROOT_ID)
            tree.addAll(artistAlbums, item.mediaId)
        }

        albums.forEach { album ->
            val tracks = tracksByAlbum[album.id].orEmpty()
                .sortedWith(compareBy({ it.trackId }, { it.title.lowercase() }))
            val item = albumItems.getValue(album.id)
            tree.add(item, SMP_ALBUMS_ROOT_ID)
            tree.addAll(tracks.map { trackItem(it) }, item.mediaId)
        }

        libraryTracks.all.forEach { track ->
            val item = trackItem(track)
            tree.add(item, SMP_TRACKS_ROOT_ID)
            tree.titleMap[track.title.lowercase()] = item
        }

        genres.forEach { genre ->
            val item = genre.toMediaItem()
            tree.add(item, SMP_GENRES_ROOT_ID)
            tree.addAll(tracksByGenre[genre.id].orEmpty().map { trackItem(it) }, item.mediaId)
        }

        return BuiltTree(tree.nodes, tree.titleMap)
    }

    private fun getNode(id: String) = treeNodes[id]

    private fun getMediaItemFromQueueItem(queueItem: QueueItem) = getNode(queueItem.trackId.toString())?.item

    private fun buildRootItem() = buildMediaItem(
        title = context.getString(com.simplemobiletools.commons.R.string.root),
        mediaId = SMP_ROOT_ID,
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    )
}

private class MediaItemNode(val item: MediaItem) {
    private val children: MutableList<MediaItem> = ArrayList()

    fun addChild(item: MediaItem) = children.add(item)

    fun getChildren() = children.toList()
}

private class BuiltTree(
    val nodes: Map<String, MediaItemNode>,
    val titleMap: Map<String, MediaItem>
)

private class TreeBuilder(rootItem: MediaItem, categories: List<MediaItem>) {
    val nodes = HashMap<String, MediaItemNode>()
    val titleMap = HashMap<String, MediaItem>()

    init {
        val rootNode = MediaItemNode(rootItem)
        nodes[rootItem.mediaId] = rootNode
        categories.forEach {
            nodes[it.mediaId] = MediaItemNode(it)
            rootNode.addChild(it)
        }
    }

    fun add(item: MediaItem, parentId: String? = null) {
        nodes[item.mediaId] = MediaItemNode(item)
        if (parentId != null) {
            nodes[parentId]?.addChild(item)
        }
    }

    fun addAll(items: List<MediaItem>, parentId: String) = items.forEach { add(it, parentId) }
}

private enum class RootCategories(@StringRes val titleRes: Int, @DrawableRes val drawableRes: Int, val mediaId: String, val mediaType: @MediaType Int) {
    PLAYLISTS(R.string.playlists, R.drawable.ic_playlist_vector, SMP_PLAYLISTS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
    FOLDERS(R.string.folders, R.drawable.ic_folders_vector, SMP_FOLDERS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
    ARTISTS(R.string.artists, com.simplemobiletools.commons.R.drawable.ic_person_vector, SMP_ARTISTS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
    ALBUMS(R.string.albums, R.drawable.ic_album_vector, SMP_ALBUMS_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
    TRACKS(R.string.tracks, R.drawable.ic_music_note_vector, SMP_TRACKS_ROOT_ID, MediaMetadata.MEDIA_TYPE_PLAYLIST),
    GENRES(R.string.genres, R.drawable.ic_genre_vector, SMP_GENRES_ROOT_ID, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES);

    companion object {
        fun buildRootChildren(context: Context): List<MediaItem> {
            val rootChildren = mutableListOf<MediaItem>()
            values().forEach {
                val flag = getTabFlag(it.mediaId)
                if (context.isTabVisible(flag)) {
                    rootChildren += buildMediaItem(
                        title = context.getString(it.titleRes),
                        artworkUri = buildDrawableUri(context, it.drawableRes),
                        mediaId = it.mediaId,
                        mediaType = it.mediaType
                    )
                }
            }

            return rootChildren
        }

        private fun getTabFlag(mediaId: String): Int {
            return when (mediaId) {
                SMP_PLAYLISTS_ROOT_ID -> TAB_PLAYLISTS
                SMP_FOLDERS_ROOT_ID -> TAB_FOLDERS
                SMP_ARTISTS_ROOT_ID -> TAB_ARTISTS
                SMP_ALBUMS_ROOT_ID -> TAB_ALBUMS
                SMP_TRACKS_ROOT_ID -> TAB_TRACKS
                SMP_GENRES_ROOT_ID -> TAB_GENRES
                else -> throw IllegalArgumentException("Invalid media id: $mediaId")
            }
        }

        private fun buildDrawableUri(context: Context, drawableRes: Int): Uri? {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.resources.getResourcePackageName(drawableRes))
                .appendPath(context.resources.getResourceTypeName(drawableRes))
                .appendPath(context.resources.getResourceEntryName(drawableRes))
                .build()
        }
    }
}
