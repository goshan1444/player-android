package com.player.hifi.core.metadata

import android.content.ContentResolver
import android.net.Uri
import com.player.hifi.core.model.Track
import java.io.File

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class TrackLyrics(
    val isSynced: Boolean,
    val lines: List<LyricLine>
)

object LyricsParser {

    // Формат LRC: [mm:ss.xx] или [mm:ss.xxx]Текст строки
    private val lrcLineRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")

    fun loadLyricsForTrack(contentResolver: ContentResolver, track: Track): TrackLyrics? {
        // 1. Пытаемся найти внешний .lrc или .txt файл рядом с аудиофайлом (если есть доступ к пути)
        val baseFileName = track.fileName.substringBeforeLast('.')
        val folderFile = File(track.folderPath)
        if (folderFile.exists() && folderFile.isDirectory) {
            val lrcFile = File(folderFile, "$baseFileName.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                runCatching {
                    return parseRawLyrics(lrcFile.readText())
                }
            }
            val txtFile = File(folderFile, "$baseFileName.txt")
            if (txtFile.exists() && txtFile.canRead()) {
                runCatching {
                    return parseRawLyrics(txtFile.readText())
                }
            }
        }

        // 2. Пытаемся извлечь встроенный текст (USLT / SYLT / Vorbis LYRICS) из заголовка файла
        runCatching {
            val uri = Uri.parse(track.uriString)
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = ByteArray(128 * 1024)
                val read = stream.read(bytes)
                if (read > 0) {
                    val rawWindow = String(bytes, 0, read, Charsets.UTF_8)
                    val vorbisLyricsMatch = Regex("""LYRICS=([^\u0000]+)""", RegexOption.IGNORE_CASE).find(rawWindow)
                    if (vorbisLyricsMatch != null) {
                        return parseRawLyrics(vorbisLyricsMatch.groupValues[1])
                    }
                    // Если в тегах есть встроенная LRC-разметка
                    if (rawWindow.contains("[00:") || rawWindow.contains("[01:")) {
                        val startIdx = rawWindow.indexOf("[0")
                        if (startIdx >= 0) {
                            val endIdx = rawWindow.indexOf('\u0000', startIdx).takeIf { it > startIdx } ?: rawWindow.length
                            return parseRawLyrics(rawWindow.substring(startIdx, endIdx))
                        }
                    }
                }
            }
        }

        return null
    }

    fun parseRawLyrics(rawText: String): TrackLyrics? {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return null

        val syncedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<LyricLine>()

        for (line in rawLines) {
            val match = lrcLineRegex.matchEntire(line)
            if (match != null) {
                val mins = match.groupValues[1].toLongOrNull() ?: 0L
                val secs = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val millis = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val content = match.groupValues[4].trim()
                if (content.isNotEmpty()) {
                    val timestamp = (mins * 60_000L) + (secs * 1_000L) + millis
                    syncedLines.add(LyricLine(timestampMs = timestamp, text = content))
                }
            } else if (!line.startsWith("[ti:") && !line.startsWith("[ar:") && !line.startsWith("[al:")) {
                plainLines.add(LyricLine(timestampMs = 0L, text = line))
            }
        }

        return when {
            syncedLines.isNotEmpty() -> TrackLyrics(
                isSynced = true,
                lines = syncedLines.sortedBy { it.timestampMs }
            )
            plainLines.isNotEmpty() -> TrackLyrics(
                isSynced = false,
                lines = plainLines
            )
            else -> null
        }
    }
}