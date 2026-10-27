package com.player.hifi.core.model

import androidx.media3.common.Player

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val decodedAudioInfo: DecodedAudioInfo? = null,
    val outputRouteInfo: OutputRouteInfo? = null,
    val equalizerState: EqualizerState? = null,
    val sleepTimerRemainingMs: Long? = null,
    val errorMessage: String? = null
)