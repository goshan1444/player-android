package com.player.hifi.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.player.hifi.AppContainer
import com.player.hifi.core.database.PlaylistEntity
import com.player.hifi.core.database.PlaylistTrackCrossRef
import com.player.hifi.core.datastore.UserSettings
import com.player.hifi.core.metadata.LyricsParser
import com.player.hifi.core.metadata.OnlineArtworkFetcher
import com.player.hifi.core.metadata.TrackLyrics
import com.player.hifi.core.model.AlbumSummary
import com.player.hifi.core.model.ArtistSummary
import com.player.hifi.core.model.AudioOutputMode
import com.player.hifi.core.model.OutputRouteInfo
import com.player.hifi.core.model.PlaylistWithTracks
import com.player.hifi.core.model.ReplayGainMode
import com.player.hifi.core.model.SortOption
import com.player.hifi.core.model.ThemeMode
import com.player.hifi.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(FlowPreview::class)
class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<UserSettings> = container.preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isDownloadingArtwork = MutableStateFlow(false)
    val isDownloadingArtwork: StateFlow<Boolean> = _isDownloadingArtwork.asStateFlow()

    private val rawTracks: StateFlow<List<Track>> = container.database.libraryDao()
        .observeAllTracks()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortedFilteredTracks: StateFlow<List<Track>> = combine(
        rawTracks,
        settings.map { it.sortOption }.distinctUntilChanged(),
        _searchQuery.debounce(150)
    ) { tracks, sortOption, query ->
        val filtered = if (query.isBlank()) {
            tracks
        } else {
            val q = query.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q) ||
                        it.fileName.lowercase().contains(q)
            }
        }
        when (sortOption) {
            SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM -> filtered.sortedBy { it.album.lowercase() }
            SortOption.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.dateAddedEpochSec }
            SortOption.FILENAME -> filtered.sortedBy { it.fileName.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<AlbumSummary>> = rawTracks.map { tracks ->
        tracks.groupBy { it.albumKey }.map { (key, group) ->
            val first = group.first()
            AlbumSummary(
                albumKey = key,
                title = first.album,
                artist = first.albumArtist.ifBlank { first.artist },
                artworkUriString = group.firstNotNullOfOrNull { it.artworkUriString },
                trackCount = group.size,
                tracks = group.sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.title }))
            )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<ArtistSummary>> = rawTracks.map { tracks ->
        tracks.groupBy { it.artist }.map { (artistName, group) ->
            ArtistSummary(
                name = artistName,
                trackCount = group.size,
                albumCount = group.map { it.albumKey }.distinct().size,
                tracks = group
            )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folderMap: StateFlow<Map<String, List<Track>>> = rawTracks.map { tracks ->
        tracks.groupBy { it.folderPath }.toSortedMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val playlists: StateFlow<List<PlaylistWithTracks>> = combine(
        container.database.libraryDao().observePlaylists(),
        container.database.libraryDao().observePlaylistCrossRefs(),
        rawTracks
    ) { playlistEntities, refs, tracks ->
        val trackById = tracks.associateBy { it.id }
        playlistEntities.map { pl ->
            val plTracks = refs.filter { it.playlistId == pl.playlistId }
                .sortedBy { it.positionIndex }
                .mapNotNull { trackById[it.trackId] }
            PlaylistWithTracks(pl.playlistId, pl.name, plTracks)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentTrack: StateFlow<Track?> = combine(rawTracks, container.activeTrackId) { tracks, activeId ->
        tracks.firstOrNull { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val queueTracks: StateFlow<List<Track>> = combine(rawTracks, container.queueTrackIds) { tracks, ids ->
        val byId = tracks.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPlaying = container.isPlaying
    val currentPositionMs = container.currentPositionMs
    val durationMs = container.durationMs
    val shuffleEnabled = container.shuffleEnabled
    val repeatMode = container.repeatMode
    val playbackSpeed = container.playbackSpeed
    val playbackPitch = container.playbackPitch
    val decodedAudioInfo = container.decodedAudioInfo
    val playbackError = container.playbackError
    val equalizerState = container.equalizerController.state
    val sleepTimerRemainingMs = container.sleepTimerManager.remainingMillis
    val alacSupportReport = container.alacVerification

    suspend fun loadLyrics(contentResolver: ContentResolver, track: Track): TrackLyrics? =
        withContext(Dispatchers.IO) {
            LyricsParser.loadLyricsForTrack(contentResolver, track)
        }

    fun currentOutputRouteInfo(): OutputRouteInfo {
        return container.capabilityDetector.inspectCurrentOutputRoute(settings.value.audioOutputMode)
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun rescanLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val currentSettings = settings.value
            container.scanner.scanLibrary(
                excludedFolders = currentSettings.excludedFolders,
                safTreeUris = currentSettings.authorizedSafTreeUris
            )
            _isScanning.value = false
        }
    }

    private fun isArtworkMissingOrInvalid(track: Track): Boolean {
        val art = track.artworkUriString
        if (art.isNullOrBlank()) return true
        return !runCatching {
            val u = Uri.parse(art)
            if (u.scheme == "file") {
                val f = File(u.path ?: return@runCatching false)
                f.exists() && f.length() > 0
            } else {
                container.app.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize > 0 } ?: false
            }
        }.getOrDefault(false)
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    fun downloadMissingArtwork() {
        if (_isDownloadingArtwork.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingArtwork.value = true
            try {
                val tracks = rawTracks.value
                val missingTracks = tracks.filter { isArtworkMissingOrInvalid(it) }
                val albumsWithoutArt = missingTracks.groupBy { it.albumKey }
                for ((_, group) in albumsWithoutArt) {
                    val first = group.first()
                    val artist = first.albumArtist.ifBlank { first.artist }
                    val album = first.album
                    val artworkUri = OnlineArtworkFetcher.fetchAlbumArtwork(
                        context = container.app,
                        artist = artist,
                        album = album,
                        trackTitle = first.title
                    )
                    if (artworkUri != null) {
                        if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true) && !album.equals("AyuGram", ignoreCase = true)) {
                            container.database.libraryDao().updateAlbumArtwork(album, artworkUri)
                        }
                        group.forEach { t ->
                            container.database.libraryDao().updateTrackArtwork(t.id, artworkUri)
                        }
                    }
                }
            } finally {
                _isDownloadingArtwork.value = false
            }
        }
    }

    fun fetchArtworkForTrack(track: Track) {
        if (!isArtworkMissingOrInvalid(track)) return
        viewModelScope.launch(Dispatchers.IO) {
            val artist = track.albumArtist.ifBlank { track.artist }
            val album = track.album
            val artworkUri = OnlineArtworkFetcher.fetchAlbumArtwork(
                context = container.app,
                artist = artist,
                album = album,
                trackTitle = track.title
            )
            if (artworkUri != null) {
                if (album.isNotBlank() && !album.equals("Unknown Album", ignoreCase = true) && !album.equals("AyuGram", ignoreCase = true)) {
                    container.database.libraryDao().updateAlbumArtwork(album, artworkUri)
                }
                container.database.libraryDao().updateTrackArtwork(track.id, artworkUri)
            }
        }
    }

    fun addSafFolderAndScan(treeUriString: String) {
        viewModelScope.launch {
            container.preferences.addSafTreeUri(treeUriString)
            rescanLibrary()
        }
    }

    fun toggleExcludeFolder(folderPath: String) {
        viewModelScope.launch {
            container.preferences.toggleExcludedFolder(folderPath)
            rescanLibrary()
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) = container.playTracks(tracks, startIndex)
    fun togglePlayPause() = container.togglePlayPause()
    fun seekTo(posMs: Long) = container.seekTo(posMs)
    fun seekByDelta(deltaMs: Long) = container.seekByDelta(deltaMs)
    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) = container.setPlaybackSpeedAndPitch(speed, pitch)
    fun skipNext() = container.skipNext()
    fun skipPrevious() = container.skipPrevious()
    fun toggleShuffle() = container.toggleShuffle()
    fun cycleRepeatMode() = container.cycleRepeatMode()
    fun playNextInQueue(track: Track) = container.playNextInQueue(track)
    fun addToEndOfQueue(track: Track) = container.addToEndOfQueue(track)
    fun moveQueueItem(from: Int, to: Int) = container.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = container.removeQueueItem(index)
    fun clearQueue() = container.clearQueue()
    fun clearPlaybackError() = container.clearPlaybackError()

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            container.database.libraryDao().setFavorite(track.id, !track.isFavorite)
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            container.database.libraryDao().createPlaylist(
                PlaylistEntity(name = name.trim(), createdAtEpochMs = System.currentTimeMillis())
            )
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            container.database.libraryDao().deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String, nextIndex: Int) {
        viewModelScope.launch {
            container.database.libraryDao().addTrackToPlaylist(
                PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, positionIndex = nextIndex)
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { container.preferences.setThemeMode(mode) }
    fun setUseAmoledDark(enabled: Boolean) = viewModelScope.launch { container.preferences.setUseAmoledDark(enabled) }
    fun setUseDynamicColor(enabled: Boolean) = viewModelScope.launch { container.preferences.setUseDynamicColor(enabled) }
    fun setGaplessEnabled(enabled: Boolean) = viewModelScope.launch { container.preferences.setGaplessEnabled(enabled) }
    fun setReplayGainMode(mode: ReplayGainMode) = viewModelScope.launch { container.preferences.setReplayGainMode(mode) }
    fun setAudioOutputMode(mode: AudioOutputMode) = viewModelScope.launch { container.preferences.setAudioOutputMode(mode) }
    fun setSortOption(option: SortOption) = viewModelScope.launch { container.preferences.setSortOption(option) }

    fun setEqualizerEnabled(enabled: Boolean) {
        container.equalizerController.setEnabled(enabled)
        viewModelScope.launch {
            container.preferences.saveEqualizerState(
                enabled = enabled,
                bands = equalizerState.value.bands.associate { it.index to it.currentLevelMillibels }
            )
        }
    }

    fun setEqualizerBand(bandIndex: Short, levelMillibels: Short) {
        val updated = container.equalizerController.setBandLevel(bandIndex, levelMillibels)
        viewModelScope.launch {
            container.preferences.saveEqualizerState(equalizerState.value.isEnabled, updated)
        }
    }

    fun applyEqualizerPreset(index: Int) {
        val updated = container.equalizerController.applyPreset(index)
        viewModelScope.launch {
            container.preferences.saveEqualizerState(equalizerState.value.isEnabled, updated)
        }
    }

    fun resetEqualizerFlat() {
        val updated = container.equalizerController.resetFlat()
        viewModelScope.launch {
            container.preferences.saveEqualizerState(equalizerState.value.isEnabled, updated)
        }
    }

    fun startSleepTimer(minutes: Int) = container.sleepTimerManager.startTimer(minutes)
    fun cancelSleepTimer() = container.sleepTimerManager.cancelTimer()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(container) as T
        }
    }
}