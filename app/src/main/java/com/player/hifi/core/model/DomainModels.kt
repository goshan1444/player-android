package com.player.hifi.core.model

data class Track(
    val id: String,
    val uriString: String,
    val fileName: String,
    val folderPath: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String?,
    val trackNumber: Int?,
    val durationMs: Long,
    val dateAddedEpochSec: Long,
    val artworkUriString: String?,
    val isFavorite: Boolean,
    val lastPlayedEpochMillis: Long?,
    val playCount: Int,
    val sourceMetadata: SourceAudioMetadata
) {
    val albumKey: String
        get() = "${album.lowercase().trim()}::${albumArtist.ifBlank { artist }.lowercase().trim()}"
}

data class AlbumSummary(
    val albumKey: String,
    val title: String,
    val artist: String,
    val artworkUriString: String?,
    val trackCount: Int,
    val tracks: List<Track>
)

data class ArtistSummary(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val tracks: List<Track>
)

data class PlaylistWithTracks(
    val playlistId: Long,
    val name: String,
    val tracks: List<Track>
)

data class EqualizerBandState(
    val index: Short,
    val centerFreqHz: Int,
    val currentLevelMillibels: Short,
    val minLevelMillibels: Short,
    val maxLevelMillibels: Short
)

data class EqualizerState(
    val isSupportedByDevice: Boolean,
    val isEnabled: Boolean,
    val audioSessionId: Int,
    val bands: List<EqualizerBandState>,
    val presets: List<String>,
    val selectedPresetIndex: Int,
    val errorReason: String? = null
)