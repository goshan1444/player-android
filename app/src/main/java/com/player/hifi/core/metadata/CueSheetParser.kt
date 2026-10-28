package com.player.hifi.core.metadata

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class CueTrackEntry(
    val trackNumber: Int,
    val title: String,
    val performer: String?,
    val startPositionMs: Long,
    val endPositionMs: Long?,
    val targetAudioFileName: String? = null
)

data class ParsedCueSheet(
    val albumTitle: String?,
    val albumPerformer: String?,
    val targetAudioFileName: String?,
    val tracks: List<CueTrackEntry>
)

object CueSheetParser {

    fun parseCueFile(cueFile: File): ParsedCueSheet? {
        if (!cueFile.exists() || !cueFile.canRead()) return null
        val bytes = runCatching { cueFile.readBytes() }.getOrNull() ?: return null
        return parseCueBytes(bytes)
    }

    fun parseCueStream(inputStream: InputStream): ParsedCueSheet? {
        val bytes = runCatching { inputStream.readBytes() }.getOrNull() ?: return null
        return parseCueBytes(bytes)
    }

    fun parseCueBytes(bytes: ByteArray): ParsedCueSheet? {
        if (bytes.isEmpty()) return null
        val text = decodeCueBytes(bytes)
        return parseCueText(text)
    }

    /**
     * Tries multiple charsets common for CUE files:
     * 1. UTF-8 (handling BOM)
     * 2. Windows-1251 (Russian / Cyrillic)
     * 3. Windows-1252 / ISO-8859-1 (European)
     */
    private fun decodeCueBytes(bytes: ByteArray): String {
        // Strip UTF-8 BOM if present
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        // Try strict UTF-8
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
            if (!decoded.contains('\uFFFD')) {
                return decoded
            }
        } catch (_: Exception) {
            // Fall through to other charsets
        }

        // Try Windows-1251 (common in Cyrillic EAC rips)
        val cp1251 = runCatching { Charset.forName("windows-1251") }.getOrNull()
        if (cp1251 != null) {
            val text = String(bytes, cp1251)
            // If it has non-ASCII characters that decode into Cyrillic, use it
            if (text.any { it in '\u0400'..'\u04FF' }) {
                return text
            }
        }

        // Try Windows-1252 / ISO-8859-1
        val cp1252 = runCatching { Charset.forName("windows-1252") }.getOrNull() ?: Charsets.ISO_8859_1
        return String(bytes, cp1252)
    }

    fun parseCueText(rawCue: String): ParsedCueSheet? {
        var albumTitle: String? = null
        var albumPerformer: String? = null
        var primaryTargetAudioFile: String? = null
        var currentFile: String? = null

        data class MutableTrack(
            val number: Int,
            var title: String? = null,
            var performer: String? = null,
            var startMs: Long? = null,
            var targetFile: String? = null
        )

        val rawTracks = mutableListOf<MutableTrack>()
        var currentTrack: MutableTrack? = null

        val trackRegex = Regex("""^TRACK\s+(\d+)(?:\s+\w+)?""", RegexOption.IGNORE_CASE)
        val index01Regex = Regex("""^INDEX\s+0?1\s+(\d+):(\d+)(?::(\d+))?""", RegexOption.IGNORE_CASE)
        val fileRegex = Regex("""^FILE\s+(?:"([^"]+)"|(\S+))(?:\s+\w+)?$""", RegexOption.IGNORE_CASE)

        for (rawLine in rawCue.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("REM", ignoreCase = true)) continue

            // 1. FILE directive
            val fileMatch = fileRegex.find(line)
            if (fileMatch != null) {
                val rawTarget = (fileMatch.groups[1]?.value ?: fileMatch.groups[2]?.value).orEmpty().trim()
                val cleanFileName = unquote(rawTarget).substringAfterLast('\\').substringAfterLast('/')
                currentFile = cleanFileName
                if (primaryTargetAudioFile == null) {
                    primaryTargetAudioFile = cleanFileName
                }
                continue
            }

            // 2. TRACK directive
            val trackMatch = trackRegex.find(line)
            if (trackMatch != null) {
                currentTrack?.let { rawTracks.add(it) }
                val num = trackMatch.groupValues[1].toIntOrNull() ?: (rawTracks.size + 1)
                currentTrack = MutableTrack(number = num, targetFile = currentFile ?: primaryTargetAudioFile)
                continue
            }

            // 3. TITLE directive
            if (line.startsWith("TITLE", ignoreCase = true)) {
                val value = unquote(line.substring(5).trim())
                if (value.isNotBlank()) {
                    if (currentTrack == null) {
                        albumTitle = value
                    } else {
                        currentTrack.title = value
                    }
                }
                continue
            }

            // 4. PERFORMER directive
            if (line.startsWith("PERFORMER", ignoreCase = true)) {
                val value = unquote(line.substring(9).trim())
                if (value.isNotBlank()) {
                    if (currentTrack == null) {
                        albumPerformer = value
                    } else {
                        currentTrack.performer = value
                    }
                }
                continue
            }

            // 5. INDEX 01 mm:ss:ff (75 frames per second in Red Book CD-DA standard)
            val indexMatch = index01Regex.find(line)
            if (indexMatch != null && currentTrack != null) {
                val mm = indexMatch.groupValues[1].toLongOrNull() ?: 0L
                val ss = indexMatch.groupValues[2].toLongOrNull() ?: 0L
                val ff = indexMatch.groups[3]?.value?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: 0L
                val totalMs = (mm * 60_000L) + (ss * 1_000L) + ((ff * 1000L) / 75L)
                currentTrack.startMs = totalMs
                continue
            }
        }
        currentTrack?.let { rawTracks.add(it) }

        val validTracks = rawTracks.filter { it.startMs != null }
        if (validTracks.isEmpty()) return null

        val finalEntries = validTracks.mapIndexed { idx, item ->
            val start = item.startMs ?: 0L
            val nextTrack = validTracks.getOrNull(idx + 1)
            // If next track is in the same file, use its start as our end. Otherwise end is null.
            val nextStart = if (nextTrack != null && (nextTrack.targetFile == null || nextTrack.targetFile == item.targetFile)) {
                nextTrack.startMs
            } else null

            CueTrackEntry(
                trackNumber = item.number,
                title = item.title?.takeIf { it.isNotBlank() } ?: "Track ${item.number}",
                performer = item.performer ?: albumPerformer,
                startPositionMs = start,
                endPositionMs = nextStart,
                targetAudioFileName = item.targetFile ?: primaryTargetAudioFile
            )
        }

        return ParsedCueSheet(
            albumTitle = albumTitle,
            albumPerformer = albumPerformer,
            targetAudioFileName = primaryTargetAudioFile,
            tracks = finalEntries
        )
    }

    private fun unquote(str: String): String {
        val s = str.trim()
        return if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('\'') && s.endsWith('\''))) {
            if (s.length >= 2) s.substring(1, s.length - 1).trim() else s
        } else {
            s.removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'").trim()
        }
    }
}
