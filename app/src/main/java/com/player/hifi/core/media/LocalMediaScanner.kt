package com.player.hifi.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.player.hifi.core.database.LibraryDao
import com.player.hifi.core.database.TrackEntity
import com.player.hifi.core.metadata.AudioHeaderInspector
import com.player.hifi.core.metadata.CueSheetParser
import com.player.hifi.core.metadata.ParsedCueSheet
import com.player.hifi.core.model.BitDepthValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaScanner(
    private val context: Context,
    private val libraryDao: LibraryDao
) {
    private val supportedExtensions = setOf("mp3", "m4a", "flac", "wav", "ape", "ogg", "opus")
    private val cueCompatibleExtensions = setOf("flac", "wav", "ape", "mp3", "m4a", "ogg", "opus")

    private data class CueEntry(
        val uri: Uri?,
        val file: File?,
        val fileName: String,
        val folderPath: String,
        val relPath: String,
        val sheet: ParsedCueSheet
    )

    private data class SafFile(
        val docId: String,
        val name: String,
        val size: Long,
        val uri: Uri,
        val parentDocId: String,
        val folderDisplayName: String
    )

    suspend fun scanLibrary(
        excludedFolders: Set<String>,
        safTreeUris: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val existingById = libraryDao.getAllTracksSnapshot().associateBy { it.id }
        val discoveredEntities = LinkedHashMap<String, TrackEntity>()

        // 1. Discover all CUE files from MediaStore (if indexed by Android)
        val mediaStoreCues = queryAllMediaStoreCues()

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
                    val rawRelPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty() else ""
                    val folderPath = if (fullPath.isNotBlank()) {
                        File(fullPath).parent ?: "/Music"
                    } else {
                        rawRelPath.ifBlank { "/Music" }
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

                    // Check if MediaStore actually has an album art image file
                    val albumId = cursor.getLong(albumIdCol)
                    val mediaStoreArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                    val hasMediaStoreArt = albumId > 0 && runCatching {
                        context.contentResolver.openFileDescriptor(mediaStoreArtUri, "r")?.use { it.statSize > 0 } ?: false
                    }.getOrDefault(false)

                    // Keep previously downloaded or valid artwork if present
                    val existingArtValid = existing?.artworkUriString?.takeIf { uriStr ->
                        isArtworkUriAccessible(uriStr)
                    }

                    val artworkUriString = existingArtValid ?: if (hasMediaStoreArt) mediaStoreArtUri.toString() else null

                    val (bitDepthKind, bitDepthBits) = when (val bd = sourceMeta.sourceBitDepth) {
                        is BitDepthValue.Verified -> "VERIFIED" to bd.bits
                        is BitDepthValue.NotApplicable -> "NA" to null
                        is BitDepthValue.Unknown -> "UNKNOWN" to null
                    }

                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val cleanArtist = if (rawArtist == "<unknown>" || rawArtist.isBlank()) "Unknown Artist" else rawArtist
                    val cleanAlbum = cursor.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown Album"

                    val baseEntity = TrackEntity(
                        id = trackId,
                        uriString = contentUri.toString(),
                        fileName = fileName,
                        folderPath = folderPath,
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                        artist = cleanArtist,
                        album = cleanAlbum,
                        albumArtist = (if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null)?.takeIf { it.isNotBlank() } ?: cleanArtist,
                        genre = null,
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        durationMs = sourceMeta.durationMs.takeIf { it > 0 } ?: durationMs,
                        fileSizeBytes = sizeBytes,
                        dateAddedEpochSec = cursor.getLong(dateCol),
                        artworkUriString = artworkUriString,
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

                    // Find companion .cue in MediaStore index OR on filesystem
                    val cueEntities = if (ext in cueCompatibleExtensions) {
                        findCueTracksForAudio(
                            baseEntity = baseEntity,
                            audioFileName = fileName,
                            folderPath = folderPath,
                            relPath = rawRelPath,
                            fullPath = fullPath,
                            mediaStoreCues = mediaStoreCues,
                            artworkUri = artworkUriString.orEmpty(),
                            dateAdded = cursor.getLong(dateCol)
                        )
                    } else emptyList()

                    if (cueEntities.isNotEmpty()) {
                        cueEntities.forEach { discoveredEntities[it.id] = it }
                    } else {
                        discoveredEntities[trackId] = baseEntity
                    }
                }
            }
        }

        // 2. Scan authorized SAF tree folders with full recursive subdirectory traversal
        safTreeUris.forEach { treeUriStr ->
            runCatching {
                val treeUri = Uri.parse(treeUriStr)
                val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootFolderDisplayName = rootDocId.substringAfterLast(':').ifBlank { "Music" }

                val audioFiles = mutableListOf<SafFile>()
                val cueFiles = mutableListOf<SafFile>()

                scanSafDirectoryRecursive(
                    contentResolver = context.contentResolver,
                    treeUri = treeUri,
                    parentDocId = rootDocId,
                    folderDisplayName = rootFolderDisplayName,
                    audioFiles = audioFiles,
                    cueFiles = cueFiles
                )

                // Parse discovered CUE files
                val safCueSheets = cueFiles.mapNotNull { cf ->
                    runCatching {
                        context.contentResolver.openInputStream(cf.uri)?.use { stream ->
                            CueSheetParser.parseCueStream(stream)
                        }
                    }.getOrNull()?.let { sheet -> cf to sheet }
                }

                for (af in audioFiles) {
                    val trackId = "saf_${af.uri.toString().hashCode()}"
                    if (discoveredEntities.containsKey(trackId)) continue

                    val existing = existingById[trackId]
                    val meta = AudioHeaderInspector.inspectTrack(context.contentResolver, af.uri, existing?.durationMs ?: 0L, af.size)
                    val (bdKind, bdBits) = when (val bd = meta.sourceBitDepth) {
                        is BitDepthValue.Verified -> "VERIFIED" to bd.bits
                        is BitDepthValue.NotApplicable -> "NA" to null
                        is BitDepthValue.Unknown -> "UNKNOWN" to null
                    }

                    val existingArtValid = existing?.artworkUriString?.takeIf { uriStr ->
                        isArtworkUriAccessible(uriStr)
                    }

                    val baseSafEntity = TrackEntity(
                        id = trackId,
                        uriString = af.uri.toString(),
                        fileName = af.name,
                        folderPath = "SAF: ${af.folderDisplayName}",
                        title = af.name.substringBeforeLast('.'),
                        artist = "Local Audio",
                        album = af.folderDisplayName,
                        albumArtist = "Local Audio",
                        genre = null,
                        trackNumber = null,
                        durationMs = meta.durationMs,
                        fileSizeBytes = af.size,
                        dateAddedEpochSec = System.currentTimeMillis() / 1000L,
                        artworkUriString = existingArtValid,
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

                    // Match with SAF CUE in same folder or with matching target
                    val matchedSafCue = safCueSheets.firstOrNull { (cueFile, sheet) ->
                        val sameFolder = cueFile.parentDocId == af.parentDocId
                        val matchesName = isCueMatchingAudio(
                            audioFileName = af.name,
                            cueFileName = cueFile.name,
                            cueTarget = sheet.targetAudioFileName
                        )
                        sameFolder && matchesName
                    }

                    if (matchedSafCue != null) {
                        val cueEntities = buildCueEntities(
                            sheet = matchedSafCue.second,
                            baseEntity = baseSafEntity,
                            artworkUri = existingArtValid.orEmpty(),
                            dateAdded = System.currentTimeMillis() / 1000L
                        )
                        cueEntities.forEach { discoveredEntities[it.id] = it }
                    } else {
                        discoveredEntities[trackId] = baseSafEntity
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

    private fun isArtworkUriAccessible(uriStr: String): Boolean {
        return runCatching {
            val u = Uri.parse(uriStr)
            if (u.scheme == "file") {
                val f = File(u.path ?: return@runCatching false)
                f.exists() && f.length() > 0
            } else {
                context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize > 0 } ?: false
            }
        }.getOrDefault(false)
    }

    /**
     * Recursive directory scanner for Storage Access Framework trees.
     */
    private fun scanSafDirectoryRecursive(
        contentResolver: ContentResolver,
        treeUri: Uri,
        parentDocId: String,
        folderDisplayName: String,
        audioFiles: MutableList<SafFile>,
        cueFiles: MutableList<SafFile>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projectionSaf = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        runCatching {
            contentResolver.query(childrenUri, projectionSaf, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val mime = cursor.getString(mimeCol).orEmpty()
                    val size = cursor.getLong(sizeCol)
                    val ext = name.substringAfterLast('.', "").lowercase()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val subFolderName = if (folderDisplayName.isNotBlank()) "$folderDisplayName / $name" else name
                        scanSafDirectoryRecursive(contentResolver, treeUri, childDocId, subFolderName, audioFiles, cueFiles)
                    } else {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        if (ext == "cue") {
                            cueFiles.add(SafFile(childDocId, name, size, fileUri, parentDocId, folderDisplayName))
                        } else if (ext in supportedExtensions || mime.startsWith("audio/")) {
                            audioFiles.add(SafFile(childDocId, name, size, fileUri, parentDocId, folderDisplayName))
                        }
                    }
                }
            }
        }
    }

    /**
     * Queries MediaStore.Files for all .cue files indexed on device storage.
     */
    private fun queryAllMediaStoreCues(): List<CueEntry> {
        val result = mutableListOf<CueEntry>()
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.cue' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.CUE'"

        runCatching {
            context.contentResolver.query(filesUri, projection, selection, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val relPathCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    val data = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol).orEmpty().trim('/') else ""
                    val contentUri = ContentUris.withAppendedId(filesUri, id)

                    val sheet = runCatching {
                        context.contentResolver.openInputStream(contentUri)?.use { stream ->
                            CueSheetParser.parseCueStream(stream)
                        }
                    }.getOrNull()

                    if (sheet != null && sheet.tracks.isNotEmpty()) {
                        val folderPath = if (data.isNotBlank()) File(data).parent.orEmpty() else ""
                        result.add(
                            CueEntry(
                                uri = contentUri,
                                file = if (data.isNotBlank()) File(data) else null,
                                fileName = name,
                                folderPath = folderPath,
                                relPath = relPath,
                                sheet = sheet
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    /**
     * Finds and parses CUE sheet for an audio file:
     * 1. Direct file system companion .cue (when accessible via File API)
     * 2. MediaStore CUE entries in the same folder or matching target filename
     */
    private fun findCueTracksForAudio(
        baseEntity: TrackEntity,
        audioFileName: String,
        folderPath: String,
        relPath: String,
        fullPath: String,
        mediaStoreCues: List<CueEntry>,
        artworkUri: String,
        dateAdded: Long
    ): List<TrackEntity> {
        // 1. Check direct file system first (most reliable when accessible)
        if (fullPath.isNotBlank()) {
            val audioFile = File(fullPath)
            val dir = audioFile.parentFile
            if (dir != null && dir.exists()) {
                val baseName = audioFile.nameWithoutExtension

                // Look for baseName.cue or any .cue files in this directory
                val candidateCues = runCatching {
                    val exact = listOf(
                        File(dir, "$baseName.cue"),
                        File(dir, "$baseName.CUE"),
                        File(dir, "$baseName.Cue")
                    ).firstOrNull { it.exists() && it.canRead() }

                    if (exact != null) listOf(exact)
                    else dir.listFiles { f -> f.extension.equals("cue", ignoreCase = true) && f.canRead() }?.toList().orEmpty()
                }.getOrDefault(emptyList())

                for (cueFile in candidateCues) {
                    val sheet = CueSheetParser.parseCueFile(cueFile)
                    if (sheet != null && sheet.tracks.isNotEmpty()) {
                        val matches = isCueMatchingAudio(
                            audioFileName = audioFile.name,
                            cueFileName = cueFile.name,
                            cueTarget = sheet.targetAudioFileName
                        )
                        if (matches) {
                            return buildCueEntities(
                                sheet = sheet,
                                baseEntity = baseEntity,
                                artworkUri = artworkUri,
                                dateAdded = dateAdded
                            )
                        }
                    }
                }
            }
        }

        // 2. Check MediaStore CUE entries
        val cleanRelPath = relPath.trim('/')
        val cleanFolderPath = folderPath.trim('/')
        val matchedCueEntry = mediaStoreCues.firstOrNull { cue ->
            val sameRelPath = cleanRelPath.isNotBlank() && cue.relPath.isNotBlank() &&
                    cue.relPath.equals(cleanRelPath, ignoreCase = true)
            val sameFolder = cleanFolderPath.isNotBlank() && cue.folderPath.isNotBlank() &&
                    cue.folderPath.trim('/').equals(cleanFolderPath, ignoreCase = true)

            val nameMatches = isCueMatchingAudio(
                audioFileName = audioFileName,
                cueFileName = cue.fileName,
                cueTarget = cue.sheet.targetAudioFileName
            )

            (sameRelPath || sameFolder) && nameMatches
        }

        if (matchedCueEntry != null) {
            return buildCueEntities(
                sheet = matchedCueEntry.sheet,
                baseEntity = baseEntity,
                artworkUri = artworkUri,
                dateAdded = dateAdded
            )
        }

        return emptyList()
    }

    /**
     * Checks if a CUE sheet matches the given audio file:
     * - Target filename in CUE matches audio file (even if extension differs, e.g. wav vs flac)
     * - Same base name (album.cue <-> album.flac)
     */
    private fun isCueMatchingAudio(
        audioFileName: String,
        cueFileName: String,
        cueTarget: String?
    ): Boolean {
        val audioBase = audioFileName.substringBeforeLast('.')
        val cueBase = cueFileName.substringBeforeLast('.')

        // 1. CUE FILE directive matches audio name or base name
        if (!cueTarget.isNullOrBlank()) {
            val cleanTarget = cueTarget.substringAfterLast('\\').substringAfterLast('/')
            val targetBase = cleanTarget.substringBeforeLast('.')

            if (cleanTarget.equals(audioFileName, ignoreCase = true)) return true
            if (targetBase.equals(audioBase, ignoreCase = true)) return true
        }

        // 2. Same base name: album.cue <-> album.flac
        if (audioBase.equals(cueBase, ignoreCase = true)) return true

        return false
    }

    private fun buildCueEntities(
        sheet: ParsedCueSheet,
        baseEntity: TrackEntity,
        artworkUri: String,
        dateAdded: Long
    ): List<TrackEntity> {
        val totalDurationMs = baseEntity.durationMs

        return sheet.tracks.map { cueTrack ->
            val startMs = cueTrack.startPositionMs
            val rawNextStart = cueTrack.endPositionMs
            val endMs = if (rawNextStart != null && rawNextStart > startMs) {
                rawNextStart
            } else if (totalDurationMs > startMs) {
                totalDurationMs
            } else {
                null
            }

            val trackDurationMs = when {
                endMs != null -> (endMs - startMs).coerceAtLeast(1000L)
                else -> 180_000L
            }

            TrackEntity(
                id = "cue_${baseEntity.id}_${cueTrack.trackNumber}",
                uriString = baseEntity.uriString,
                fileName = baseEntity.fileName,
                folderPath = baseEntity.folderPath,
                title = cueTrack.title,
                artist = cueTrack.performer ?: sheet.albumPerformer ?: baseEntity.artist,
                album = sheet.albumTitle ?: baseEntity.album,
                albumArtist = sheet.albumPerformer ?: baseEntity.albumArtist,
                genre = baseEntity.genre,
                trackNumber = cueTrack.trackNumber,
                durationMs = trackDurationMs,
                fileSizeBytes = baseEntity.fileSizeBytes,
                dateAddedEpochSec = dateAdded,
                artworkUriString = artworkUri.ifBlank { baseEntity.artworkUriString },
                isFavorite = false,
                lastPlayedEpochMillis = null,
                playCount = 0,
                containerFormat = baseEntity.containerFormat,
                codec = baseEntity.codec,
                sourceBitrateBps = baseEntity.sourceBitrateBps,
                bitrateType = baseEntity.bitrateType,
                sourceBitDepthKind = baseEntity.sourceBitDepthKind,
                sourceBitDepthBits = baseEntity.sourceBitDepthBits,
                sourceSampleRateHz = baseEntity.sourceSampleRateHz,
                channelCount = baseEntity.channelCount,
                replayGainTrackDb = baseEntity.replayGainTrackDb,
                replayGainAlbumDb = baseEntity.replayGainAlbumDb,
                replayGainTrackPeak = baseEntity.replayGainTrackPeak,
                replayGainAlbumPeak = baseEntity.replayGainAlbumPeak,
                metadataSource = "CUE_SHEET",
                cueStartMs = startMs,
                cueEndMs = endMs
            )
        }
    }
}
