package com.player.hifi

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.player.hifi.core.audio.AudioRouteCapabilityDetector
import com.player.hifi.core.audio.DeviceEqualizerController
import com.player.hifi.core.audio.ReplayGainAudioProcessor
import com.player.hifi.core.database.PlayerDatabase
import com.player.hifi.core.datastore.PlayerPreferencesRepository
import com.player.hifi.core.media.AlacRenderersFactory
import com.player.hifi.core.media.LocalMediaScanner
import com.player.hifi.core.media.PlaybackService
import com.player.hifi.core.media.PlayerController
import com.player.hifi.core.media.SleepTimerManager
import com.player.hifi.core.model.AudioOutputMode
import com.player.hifi.core.model.DecodedAudioInfo
import com.player.hifi.core.model.ReplayGainMode
import com.player.hifi.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppContainer(private val app: Application) : PlayerController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val database = PlayerDatabase.getInstance(app)
    val preferences = PlayerPreferencesRepository(app)
    val scanner = LocalMediaScanner(app, database.libraryDao())
    val replayGainProcessor = ReplayGainAudioProcessor()
    val equalizerController = DeviceEqualizerController()
    val capabilityDetector = AudioRouteCapabilityDetector(app)
    val alacVerification = AlacRenderersFactory.verifyAlacDecoderAvailability()

    @Volatile
    var currentAudioOutputMode: AudioOutputMode = AudioOutputMode.STANDARD
        private set

    private var mediaController: MediaController? = null
    private var attachedExoPlayer: Player? = null

    private val _decodedAudioInfo = MutableStateFlow<DecodedAudioInfo?>(null)
    val decodedAudioInfo: StateFlow<DecodedAudioInfo?> = _decodedAudioInfo.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    override val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _activeTrackId = MutableStateFlow<String?>(null)
    override val activeTrackId: StateFlow<String?> = _activeTrackId.asStateFlow()

    private val _queueTrackIds = MutableStateFlow<List<String>>(emptyList())
    override val queueTrackIds: StateFlow<List<String>> = _queueTrackIds.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    override val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    override val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    val sleepTimerManager = SleepTimerManager(
        scope = scope,
        preferences = preferences,
        onVolumeScaleChanged = { scale ->
            (mediaController ?: attachedExoPlayer)?.volume = scale.coerceIn(0f, 1f)
        },
        onTimerExpired = { pause() }
    )

    private var trackLookup: Map<String, Track> = emptyMap()

    init {
        scope.launch {
            database.libraryDao().observeAllTracks().collect { entities ->
                trackLookup = entities.associate { it.id to it.toDomain() }
                syncActiveTrackReplayGain()
            }
        }
        scope.launch {
            preferences.settingsFlow.collect { settings ->
                currentAudioOutputMode = settings.audioOutputMode
                syncActiveTrackReplayGain(settings.replayGainMode)
            }
        }
        scope.launch {
            while (isActive) {
                val p = mediaController ?: attachedExoPlayer
                if (p != null && p.isPlaying) {
                    _currentPositionMs.value = p.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = p.duration.coerceAtLeast(0L)
                }
                delay(250L)
            }
        }
        connectMediaController()
    }

    private fun connectMediaController() {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            runCatching {
                val controller = future.get()
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                        _durationMs.value = controller.duration.coerceAtLeast(0L)
                    }
                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleEnabled.value = shuffleModeEnabled
                    }
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }
                })
            }
        }, MoreExecutors.directExecutor())
    }

    fun attachServicePlayer(player: Player) {
        attachedExoPlayer = player
    }

    fun updateDecodedAudioInfo(info: DecodedAudioInfo) {
        _decodedAudioInfo.value = info
    }

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        scope.launch {
            val settings = preferences.settingsFlow.first()
            equalizerController.bindToAudioSession(
                audioSessionId = audioSessionId,
                initialEnabled = settings.equalizerEnabled,
                savedBandLevels = settings.equalizerBandsMillibels
            )
        }
    }

    fun onActiveMediaItemChanged(mediaId: String?) {
        _activeTrackId.value = mediaId
        syncActiveTrackReplayGain()
        if (mediaId != null) {
            scope.launch {
                database.libraryDao().recordTrackPlayed(mediaId, System.currentTimeMillis())
                preferences.savePlaybackSessionState(_queueTrackIds.value, mediaId, 0L)
            }
        }
    }

    fun onPlaybackError(message: String) {
        _playbackError.value = message
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    private fun syncActiveTrackReplayGain(overrideMode: ReplayGainMode? = null) {
        scope.launch {
            val mode = overrideMode ?: preferences.settingsFlow.first().replayGainMode
            val track = _activeTrackId.value?.let { trackLookup[it] }
            val meta = track?.sourceMetadata
            replayGainProcessor.updateSettings(
                mode = mode,
                trackGainDb = meta?.replayGainTrackDb,
                albumGainDb = meta?.replayGainAlbumDb,
                trackPeak = meta?.replayGainTrackPeak,
                albumPeak = meta?.replayGainAlbumPeak
            )
        }
    }

    override fun playTracks(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val player = mediaController ?: attachedExoPlayer ?: return
        _playbackError.value = null

        val mediaItems = tracks.map { it.toMediaItem() }
        _queueTrackIds.value = tracks.map { it.id }
        val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        _activeTrackId.value = tracks[safeIndex].id

        player.volume = 1.0f
        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.play()
    }

    override fun togglePlayPause() {
        val player = mediaController ?: attachedExoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.volume = 1.0f
            player.play()
        }
    }

    override fun pause() {
        (mediaController ?: attachedExoPlayer)?.pause()
    }

    override fun seekTo(positionMs: Long) {
        val player = mediaController ?: attachedExoPlayer ?: return
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        _currentPositionMs.value = target
    }

    fun seekByDelta(deltaMs: Long) {
        val player = mediaController ?: attachedExoPlayer ?: return
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = (player.currentPosition + deltaMs).coerceIn(0L, dur)
        seekTo(target)
    }

    fun setPlaybackSpeedAndPitch(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        val safePitch = pitch.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = safeSpeed
        _playbackPitch.value = safePitch
        (mediaController ?: attachedExoPlayer)?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
    }

    override fun skipNext() {
        (mediaController ?: attachedExoPlayer)?.seekToNext()
    }

    override fun skipPrevious() {
        (mediaController ?: attachedExoPlayer)?.seekToPrevious()
    }

    override fun toggleShuffle() {
        val player = mediaController ?: attachedExoPlayer ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    override fun cycleRepeatMode() {
        val player = mediaController ?: attachedExoPlayer ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun playNextInQueue(track: Track) {
        val player = mediaController ?: attachedExoPlayer ?: return
        val insertIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertIndex, track.toMediaItem())
        val updated = _queueTrackIds.value.toMutableList()
        updated.add(insertIndex.coerceAtMost(updated.size), track.id)
        _queueTrackIds.value = updated
    }

    override fun addToEndOfQueue(track: Track) {
        val player = mediaController ?: attachedExoPlayer ?: return
        player.addMediaItem(track.toMediaItem())
        _queueTrackIds.value = _queueTrackIds.value + track.id
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val player = mediaController ?: attachedExoPlayer ?: return
        if (fromIndex !in 0 until player.mediaItemCount || toIndex !in 0 until player.mediaItemCount) return
        player.moveMediaItem(fromIndex, toIndex)
        val updated = _queueTrackIds.value.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        _queueTrackIds.value = updated
    }

    override fun removeQueueItem(index: Int) {
        val player = mediaController ?: attachedExoPlayer ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        val updated = _queueTrackIds.value.toMutableList()
        updated.removeAt(index)
        _queueTrackIds.value = updated
    }

    override fun clearQueue() {
        val player = mediaController ?: attachedExoPlayer ?: return
        player.clearMediaItems()
        _queueTrackIds.value = emptyList()
        _activeTrackId.value = null
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUriString?.let { Uri.parse(it) })
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(uriString))
            .setMediaMetadata(metadata)
            .build()
    }
}

class PlayerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}