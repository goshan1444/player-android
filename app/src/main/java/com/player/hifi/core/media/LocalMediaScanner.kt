package com.player.hifi.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.player.hifi.core.database.LibraryDao
import com.player.hifi.core.database.TrackEntity
import com.player.hifi.core.metadata.AudioHeaderInspector
import com.player.hifi.core.model.BitDepthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaScanner(
    private val context: Context,
    private val libraryDao: LibraryDao
) {
    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav")

    suspend fun scanLibrary(
        excludedFolders: Set<String>,
        safTreeUris: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val existingById = libraryDao.getAllTracksSnapshot().associateBy { it.id }
        val discoveredEntities = LinkedHashMap<String, TrackEntity>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val fileName = cursor.getString(nameCol).orEmpty()
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext !in supportedExtensions) continue

                    val fullPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val folderPath = if (fullPath.isNotBlank()) {
                        File(fullPath).parent ?: "/Music"
                    } else {
                        cursor.getString(relPathCol)?.ifBlank { "/Music" } ?: "/Music"
                    }

                    if (excludedFolders.any { folderPath.startsWith(it) }) continue

                    val mediaId = cursor.getLong(idCol)
                    val trackId = "ms_$mediaId"
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val durationMs = cursor.getLong(durationCol)
                    val sizeBytes = cursor.getLong(sizeCol)
                    val existing = existingById[trackId]

                    val sourceMeta = if (existing != null && existing.fileSizeBytes == sizeBytes && existing.durationMs == durationMs) {
                        existing.toDomain().sourceMetadata
                    } else {
                        AudioHeaderInspector.inspectTrack(context.contentResolver, contentUri, durationMs, sizeBytes)
                    }

                    val albumId = cursor.getLong(albumIdCol)
                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val (bitDepthKind, bitDepthBits) = when (val bd = sourceMeta.sourceBitDepth) {
                        is BitDepthValue.Verified -> "VERIFIED" to bd.bits
                        is BitDepthValue.NotApplicable -> "NA" to null
                        is BitDepthValue.Unknown -> "UNKNOWN" to null
                    }

                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val cleanArtist = if (rawArtist == "<unknown>" || rawArtist.isBlank()) "Unknown Artist" else rawArtist

                    discoveredEntities[trackId] = TrackEntity(
                        id = trackId,
                        uriString = contentUri.toString(),
                        fileName = fileName,
                        folderPath = folderPath,
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                        artist = cleanArtist,
                        album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown Album",
                        albumArtist = (if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null)?.takeIf { it.isNotBlank() } ?: cleanArtist,
                        genre = null,
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        durationMs = sourceMeta.durationMs.takeIf { it > 0 } ?: durationMs,
                        fileSizeBytes = sizeBytes,
                        dateAddedEpochSec = cursor.getLong(dateCol),
                        artworkUriString = artworkUri,
                        isFavorite = existing?.isFavorite ?: false,
                        lastPlayedEpochMillis = existing?.lastPlayedEpochMillis,
                        playCount = existing?.playCount ?: 0,
                        containerFormat = sourceMeta.containerFormat.name,
                        codec = sourceMeta.codec.name,
                        sourceBitrateBps = sourceMeta.sourceBitrateBps,
                        bitrateType = sourceMeta.bitrateType.name,
                        sourceBitDepthKind = bitDepthKind,
                        sourceBitDepthBits = bitDepthBits,
                        sourceSampleRateHz = sourceMeta.sourceSampleRateHz,
                        channelCount = sourceMeta.channelCount,
                        replayGainTrackDb = sourceMeta.replayGainTrackDb,
                        replayGainAlbumDb = sourceMeta.replayGainAlbumDb,
                        replayGainTrackPeak = sourceMeta.replayGainTrackPeak,
                        replayGainAlbumPeak = sourceMeta.replayGainAlbumPeak,
                        metadataSource = sourceMeta.metadataSource.name
                    )
                }
            }
        }

        safTreeUris.forEach { treeUriStr ->
            runCatching {
                val treeUri = Uri.parse(treeUriStr)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                val projectionSaf = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                context.contentResolver.query(childrenUri, projectionSaf, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val childDocId = c.getString(0)
                        val name = c.getString(1).orEmpty()
                        val mime = c.getString(2).orEmpty()
                        val size = c.getLong(3)
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext !in supportedExtensions && !mime.startsWith("audio/")) continue

                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val trackId = "saf_${fileUri.toString().hashCode()}"
                        if (discoveredEntities.containsKey(trackId)) continue

                        val existing = existingById[trackId]
                        val meta = AudioHeaderInspector.inspectTrack(context.contentResolver, fileUri, existing?.durationMs ?: 0L, size)
                        val (bdKind, bdBits) = when (val bd = meta.sourceBitDepth) {
                            is BitDepthValue.Verified -> "VERIFIED" to bd.bits
                            is BitDepthValue.NotApplicable -> "NA" to null
                            is BitDepthValue.Unknown -> "UNKNOWN" to null
                        }
                        discoveredEntities[trackId] = TrackEntity(
                            id = trackId,
                            uriString = fileUri.toString(),
                            fileName = name,
                            folderPath = "SAF: ${docId.substringAfterLast(':')}",
                            title = name.substringBeforeLast('.'),
                            artist = "Local Folder Track",
                            album = docId.substringAfterLast(':').ifBlank { "SAF Folder" },
                            albumArtist = "Local Folder Track",
                            genre = null,
                            trackNumber = null,
                            durationMs = meta.durationMs,
                            fileSizeBytes = size,
                            dateAddedEpochSec = System.currentTimeMillis() / 1000L,
                            artworkUriString = null,
                            isFavorite = existing?.isFavorite ?: false,
                            lastPlayedEpochMillis = existing?.lastPlayedEpochMillis,
                            playCount = existing?.playCount ?: 0,
                            containerFormat = meta.containerFormat.name,
                            codec = meta.codec.name,
                            sourceBitrateBps = meta.sourceBitrateBps,
                            bitrateType = meta.bitrateType.name,
                            sourceBitDepthKind = bdKind,
                            sourceBitDepthBits = bdBits,
                            sourceSampleRateHz = meta.sourceSampleRateHz,
                            channelCount = meta.channelCount,
                            replayGainTrackDb = meta.replayGainTrackDb,
                            replayGainAlbumDb = meta.replayGainAlbumDb,
                            replayGainTrackPeak = meta.replayGainTrackPeak,
                            replayGainAlbumPeak = meta.replayGainAlbumPeak,
                            metadataSource = meta.metadataSource.name
                        )
                    }
                }
            }
        }

        val allEntities = discoveredEntities.values.toList()
        libraryDao.upsertTracks(allEntities)
        if (allEntities.isNotEmpty()) {
            libraryDao.pruneRemovedTracks(allEntities.map { it.id })
        }
        allEntities.size
    }
}