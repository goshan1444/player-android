package com.player.hifi.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.player.hifi.core.model.AudioCodec
import com.player.hifi.core.model.BitDepthValue
import com.player.hifi.core.model.BitrateType
import com.player.hifi.core.model.ContainerFormat
import com.player.hifi.core.model.MetadataSource
import com.player.hifi.core.model.SourceAudioMetadata
import com.player.hifi.core.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
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
    val fileSizeBytes: Long,
    val dateAddedEpochSec: Long,
    val artworkUriString: String?,
    val isFavorite: Boolean,
    val lastPlayedEpochMillis: Long?,
    val playCount: Int,
    val containerFormat: String,
    val codec: String,
    val sourceBitrateBps: Int?,
    val bitrateType: String,
    val sourceBitDepthKind: String,
    val sourceBitDepthBits: Int?,
    val sourceSampleRateHz: Int?,
    val channelCount: Int?,
    val replayGainTrackDb: Float?,
    val replayGainAlbumDb: Float?,
    val replayGainTrackPeak: Float?,
    val replayGainAlbumPeak: Float?,
    val metadataSource: String,
    // CUE sheet virtual track fields
    val cueStartMs: Long? = null,
    val cueEndMs: Long? = null
) {
    fun toDomain(): Track {
        val bitDepth = when (sourceBitDepthKind) {
            "VERIFIED" -> sourceBitDepthBits?.let { BitDepthValue.Verified(it) } ?: BitDepthValue.Unknown
            "NA" -> BitDepthValue.NotApplicable
            else -> BitDepthValue.Unknown
        }
        return Track(
            id = id,
            uriString = uriString,
            fileName = fileName,
            folderPath = folderPath,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            trackNumber = trackNumber,
            durationMs = durationMs,
            dateAddedEpochSec = dateAddedEpochSec,
            artworkUriString = artworkUriString,
            isFavorite = isFavorite,
            lastPlayedEpochMillis = lastPlayedEpochMillis,
            playCount = playCount,
            sourceMetadata = SourceAudioMetadata(
                containerFormat = runCatching { ContainerFormat.valueOf(containerFormat) }.getOrDefault(ContainerFormat.UNKNOWN),
                codec = runCatching { AudioCodec.valueOf(codec) }.getOrDefault(AudioCodec.UNKNOWN),
                sourceBitrateBps = sourceBitrateBps,
                bitrateType = runCatching { BitrateType.valueOf(bitrateType) }.getOrDefault(BitrateType.UNKNOWN),
                sourceBitDepth = bitDepth,
                sourceSampleRateHz = sourceSampleRateHz,
                channelCount = channelCount,
                durationMs = durationMs,
                fileSizeBytes = fileSizeBytes,
                replayGainTrackDb = replayGainTrackDb,
                replayGainAlbumDb = replayGainAlbumDb,
                replayGainTrackPeak = replayGainTrackPeak,
                replayGainAlbumPeak = replayGainAlbumPeak,
                metadataSource = runCatching { MetadataSource.valueOf(metadataSource) }.getOrDefault(MetadataSource.FALLBACK_MEDIASTORE)
            ),
            cueStartMs = cueStartMs,
            cueEndMs = cueEndMs
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "positionIndex"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val positionIndex: Int
)
