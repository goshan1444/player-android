package com.player.hifi.core.media

import com.player.hifi.core.model.Track
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {
    val activeTrackId: StateFlow<String?>
    val queueTrackIds: StateFlow<List<String>>
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val shuffleEnabled: StateFlow<Boolean>
    val repeatMode: StateFlow<Int>
    val playbackError: StateFlow<String?>

    fun playTracks(tracks: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun pause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun toggleShuffle()
    fun cycleRepeatMode()
    fun playNextInQueue(track: Track)
    fun addToEndOfQueue(track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun removeQueueItem(index: Int)
    fun clearQueue()
}