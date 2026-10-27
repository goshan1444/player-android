package com.player.hifi.core.metadata

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.player.hifi.core.model.AudioCodec
import com.player.hifi.core.model.BitDepthValue
import com.player.hifi.core.model.BitrateType
import com.player.hifi.core.model.ContainerFormat
import com.player.hifi.core.model.MetadataSource
import com.player.hifi.core.model.SourceAudioMetadata
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioHeaderInspector {

    fun inspectTrack(
        contentResolver: ContentResolver,
        uri: Uri,
        fallbackDurationMs: Long,
        fileSizeBytes: Long
    ): SourceAudioMetadata {
        var headerBytes = ByteArray(0)
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                headerBytes = stream.readNBytesCompat(64 * 1024)
            }
        }

        if (headerBytes.size >= 42 &&
            headerBytes[0] == 0x66.toByte() &&
            headerBytes[1] == 0x4C.toByte() &&
            headerBytes[2] == 0x61.toByte() &&
            headerBytes[3] == 0x43.toByte()
        ) {
            return parseFlacHeader(headerBytes, fallbackDurationMs, fileSizeBytes)
        }

        if (headerBytes.size >= 44 &&
            headerBytes.decodeAscii(0, 4) == "RIFF" &&
            headerBytes.decodeAscii(8, 4) == "WAVE"
        ) {
            return parseWavHeader(headerBytes, fallbackDurationMs, fileSizeBytes)
        }

        if (headerBytes.size >= 32 && headerBytes.decodeAscii(4, 4) == "ftyp") {
            return parseM4aContainer(contentResolver, uri, headerBytes, fallbackDurationMs, fileSizeBytes)
        }

        return parseMp3OrGeneric(contentResolver, uri, headerBytes, fallbackDurationMs, fileSizeBytes)
    }

    private fun parseFlacHeader(
        bytes: ByteArray,
        fallbackDurationMs: Long,
        fileSizeBytes: Long
    ): SourceAudioMetadata {
        val b18 = bytes[18].toInt() and 0xFF
        val b19 = bytes[19].toInt() and 0xFF
        val b20 = bytes[20].toInt() and 0xFF
        val b21 = bytes[21].toInt() and 0xFF
        val b22 = bytes[22].toInt() and 0xFF
        val b23 = bytes[23].toInt() and 0xFF
        val b24 = bytes[24].toInt() and 0xFF
        val b25 = bytes[25].toInt() and 0xFF

        val sampleRate = (b18 shl 12) or (b19 shl 4) or (b20 ushr 4)
        val channels = ((b20 shr 1) and 0x07) + 1
        val bitsPerSample = (((b20 and 0x01) shl 4) or (b21 ushr 4)) + 1
        val totalSamples = ((b21.toLong() and 0x0F) shl 32) or
                (b22.toLong() shl 24) or
                (b23.toLong() shl 16) or
                (b24.toLong() shl 8) or
                b25.toLong()

        val durationMs = if (sampleRate > 0 && totalSamples > 0) {
            (totalSamples * 1000L) / sampleRate
        } else {
            fallbackDurationMs
        }

        val audioPayloadBytes = (fileSizeBytes - 8192L).coerceAtLeast(fileSizeBytes * 95 / 100)
        val estimatedBitrate = if (durationMs > 0 && audioPayloadBytes > 0) {
            ((audioPayloadBytes * 8L * 1000L) / durationMs).toInt()
        } else null

        val rg = ReplayGainParser.parseFromRawBytes(bytes)

        return SourceAudioMetadata(
            containerFormat = ContainerFormat.FLAC,
            codec = AudioCodec.FLAC,
            sourceBitrateBps = estimatedBitrate,
            bitrateType = BitrateType.VBR_ESTIMATED,
            sourceBitDepth = if (bitsPerSample in 8..32) BitDepthValue.Verified(bitsPerSample) else BitDepthValue.Unknown,
            sourceSampleRateHz = sampleRate.takeIf { it > 0 },
            channelCount = channels.takeIf { it > 0 },
            durationMs = durationMs,
            fileSizeBytes = fileSizeBytes,
            replayGainTrackDb = rg.trackGainDb,
            replayGainAlbumDb = rg.albumGainDb,
            replayGainTrackPeak = rg.trackPeak,
            replayGainAlbumPeak = rg.albumPeak,
            metadataSource = MetadataSource.BINARY_HEADER_INSPECTION
        )
    }

    private fun parseWavHeader(
        bytes: ByteArray,
        fallbackDurationMs: Long,
        fileSizeBytes: Long
    ): SourceAudioMetadata {
        var offset = 12
        var channels: Int? = null
        var sampleRate: Int? = null
        var bitsPerSample: Int? = null
        var byteRate: Int? = null

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        while (offset + 8 <= bytes.size) {
            val chunkId = bytes.decodeAscii(offset, 4)
            val chunkSize = buf.getInt(offset + 4)
            if (chunkId == "fmt " && offset + 24 <= bytes.size) {
                channels = buf.getShort(offset + 10).toInt() and 0xFFFF
                sampleRate = buf.getInt(offset + 12)
                byteRate = buf.getInt(offset + 16)
                bitsPerSample = buf.getShort(offset + 22).toInt() and 0xFFFF
                break
            }
            if (chunkSize <= 0) break
            offset += 8 + chunkSize + (chunkSize and 1)
        }

        val exactBitrate = if (sampleRate != null && channels != null && bitsPerSample != null) {
            sampleRate * channels * bitsPerSample
        } else {
            byteRate?.times(8)
        }

        val rg = ReplayGainParser.parseFromRawBytes(bytes)

        return SourceAudioMetadata(
            containerFormat = ContainerFormat.WAV,
            codec = AudioCodec.PCM,
            sourceBitrateBps = exactBitrate,
            bitrateType = BitrateType.PCM_EXACT,
            sourceBitDepth = if (bitsPerSample != null && bitsPerSample in 8..64) {
                BitDepthValue.Verified(bitsPerSample)
            } else {
                BitDepthValue.Unknown
            },
            sourceSampleRateHz = sampleRate,
            channelCount = channels,
            durationMs = fallbackDurationMs,
            fileSizeBytes = fileSizeBytes,
            replayGainTrackDb = rg.trackGainDb,
            replayGainAlbumDb = rg.albumGainDb,
            replayGainTrackPeak = rg.trackPeak,
            replayGainAlbumPeak = rg.albumPeak,
            metadataSource = MetadataSource.BINARY_HEADER_INSPECTION
        )
    }

    private fun parseM4aContainer(
        contentResolver: ContentResolver,
        uri: Uri,
        headerBytes: ByteArray,
        fallbackDurationMs: Long,
        fileSizeBytes: Long
    ): SourceAudioMetadata {
        val alacIndex = headerBytes.indexOfAscii("alac")
        val mp4aIndex = headerBytes.indexOfAscii("mp4a")

        var detectedCodec = when {
            alacIndex >= 0 -> AudioCodec.ALAC
            mp4aIndex >= 0 -> AudioCodec.AAC
            else -> AudioCodec.UNKNOWN
        }

        var alacBitDepth: Int? = null
        var alacSampleRate: Int? = null
        var alacChannels: Int? = null

        val secondAlac = if (alacIndex >= 0) headerBytes.indexOfAscii("alac", alacIndex + 4) else -1
        val cookieStart = if (secondAlac >= 0) secondAlac else alacIndex
        if (cookieStart >= 0 && cookieStart + 32 <= headerBytes.size) {
            val candidateBitDepth = headerBytes[cookieStart + 13].toInt() and 0xFF
            val candidateChannels = headerBytes[cookieStart + 17].toInt() and 0xFF
            val candidateSampleRate = ByteBuffer.wrap(headerBytes, cookieStart + 28, 4).order(ByteOrder.BIG_ENDIAN).int
            if (candidateBitDepth in listOf(16, 20, 24, 32) && candidateChannels in 1..8 && candidateSampleRate in 8000..384000) {
                alacBitDepth = candidateBitDepth
                alacChannels = candidateChannels
                alacSampleRate = candidateSampleRate
            }
        }

        var extractorSampleRate: Int? = null
        var extractorChannels: Int? = null
        var extractorBitrate: Int? = null

        val extractor = MediaExtractor()
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        if (mime.contains("alac", ignoreCase = true)) detectedCodec = AudioCodec.ALAC
                        else if (mime.contains("mp4a") || mime.contains("aac")) detectedCodec = AudioCodec.AAC
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            extractorSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            extractorChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            extractorBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                        }
                        break
                    }
                }
            }
        }.also { runCatching { extractor.release() } }

        val retrieverBitrate = extractRetrieverBitrate(contentResolver, uri)
        val declaredBitrate = extractorBitrate ?: retrieverBitrate
        val calculatedBitrate = if (fallbackDurationMs > 0 && fileSizeBytes > 0) {
            val netBytes = (fileSizeBytes - 4096L).coerceAtLeast(fileSizeBytes * 96 / 100)
            ((netBytes * 8L * 1000L) / fallbackDurationMs).toInt()
        } else null

        val finalBitDepth = when (detectedCodec) {
            AudioCodec.ALAC -> if (alacBitDepth != null) BitDepthValue.Verified(alacBitDepth) else BitDepthValue.Verified(16)
            AudioCodec.AAC -> BitDepthValue.NotApplicable
            else -> BitDepthValue.Unknown
        }

        val rg = ReplayGainParser.parseFromRawBytes(headerBytes)

        return SourceAudioMetadata(
            containerFormat = ContainerFormat.M4A,
            codec = detectedCodec,
            sourceBitrateBps = declaredBitrate ?: calculatedBitrate,
            bitrateType = if (declaredBitrate != null && detectedCodec == AudioCodec.AAC) BitrateType.CBR else BitrateType.VBR_ESTIMATED,
            sourceBitDepth = finalBitDepth,
            sourceSampleRateHz = alacSampleRate ?: extractorSampleRate,
            channelCount = alacChannels ?: extractorChannels,
            durationMs = fallbackDurationMs,
            fileSizeBytes = fileSizeBytes,
            replayGainTrackDb = rg.trackGainDb,
            replayGainAlbumDb = rg.albumGainDb,
            replayGainTrackPeak = rg.trackPeak,
            replayGainAlbumPeak = rg.albumPeak,
            metadataSource = MetadataSource.BINARY_HEADER_INSPECTION
        )
    }

    private fun parseMp3OrGeneric(
        contentResolver: ContentResolver,
        uri: Uri,
        headerBytes: ByteArray,
        fallbackDurationMs: Long,
        fileSizeBytes: Long
    ): SourceAudioMetadata {
        val isVbrHeaderPresent = headerBytes.indexOfAscii("Xing") >= 0 ||
                headerBytes.indexOfAscii("Info") >= 0 ||
                headerBytes.indexOfAscii("VBRI") >= 0

        var sampleRate: Int? = null
        var channels: Int? = null
        var extractorBitrate: Int? = null
        var mimeType = ""

        val extractor = MediaExtractor()
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        mimeType = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            extractorBitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                        }
                        break
                    }
                }
            }
        }.also { runCatching { extractor.release() } }

        val id3TagSize = parseId3v2Size(headerBytes)
        val audioDataBytes = (fileSizeBytes - id3TagSize).coerceAtLeast(0L)
        val estimatedVbrBitrate = if (fallbackDurationMs > 0 && audioDataBytes > 0) {
            ((audioDataBytes * 8L * 1000L) / fallbackDurationMs).toInt()
        } else null

        val retrieverBitrate = extractRetrieverBitrate(contentResolver, uri)
        val resolvedBitrate = if (isVbrHeaderPresent) {
            estimatedVbrBitrate ?: extractorBitrate ?: retrieverBitrate
        } else {
            extractorBitrate ?: retrieverBitrate ?: estimatedVbrBitrate
        }

        val isMp3 = mimeType.contains("mpeg") || headerBytes.decodeAscii(0, 3) == "ID3" ||
                (headerBytes.size >= 2 && (headerBytes[0].toInt() and 0xFF) == 0xFF && ((headerBytes[1].toInt() and 0xE0) == 0xE0))

        val rg = ReplayGainParser.parseFromRawBytes(headerBytes)

        return SourceAudioMetadata(
            containerFormat = if (isMp3) ContainerFormat.MP3 else ContainerFormat.UNKNOWN,
            codec = if (isMp3) AudioCodec.MP3 else AudioCodec.UNKNOWN,
            sourceBitrateBps = resolvedBitrate,
            bitrateType = when {
                isVbrHeaderPresent -> BitrateType.VBR_ESTIMATED
                extractorBitrate != null || retrieverBitrate != null -> BitrateType.CBR
                resolvedBitrate != null -> BitrateType.VBR_ESTIMATED
                else -> BitrateType.UNKNOWN
            },
            sourceBitDepth = if (isMp3) BitDepthValue.NotApplicable else BitDepthValue.Unknown,
            sourceSampleRateHz = sampleRate,
            channelCount = channels,
            durationMs = fallbackDurationMs,
            fileSizeBytes = fileSizeBytes,
            replayGainTrackDb = rg.trackGainDb,
            replayGainAlbumDb = rg.albumGainDb,
            replayGainTrackPeak = rg.trackPeak,
            replayGainAlbumPeak = rg.albumPeak,
            metadataSource = MetadataSource.MEDIA_EXTRACTOR_AND_TAGS
        )
    }

    private fun parseId3v2Size(bytes: ByteArray): Long {
        if (bytes.size >= 10 && bytes.decodeAscii(0, 3) == "ID3") {
            val s0 = bytes[6].toInt() and 0x7F
            val s1 = bytes[7].toInt() and 0x7F
            val s2 = bytes[8].toInt() and 0x7F
            val s3 = bytes[9].toInt() and 0x7F
            return ((s0 shl 21) or (s1 shl 14) or (s2 shl 7) or s3).toLong() + 10L
        }
        return 0L
    }

    private fun extractRetrieverBitrate(contentResolver: ContentResolver, uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun InputStream.readNBytesCompat(maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = read(buffer, total, maxBytes - total)
            if (read <= 0) break
            total += read
        }
        return if (total == maxBytes) buffer else buffer.copyOf(total)
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String {
        if (offset < 0 || offset + length > size) return ""
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun ByteArray.indexOfAscii(needle: String, startIndex: Int = 0): Int {
        val target = needle.toByteArray(Charsets.US_ASCII)
        if (target.isEmpty() || size < target.size) return -1
        for (i in startIndex..(size - target.size)) {
            var match = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}