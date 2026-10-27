package com.player.hifi.core.metadata

import kotlin.math.min
import kotlin.math.pow

data class ParsedReplayGain(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null
)

object ReplayGainParser {

    private val trackGainRegex = Regex("""REPLAYGAIN_TRACK_GAIN[=\x00\s]+([+-]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val albumGainRegex = Regex("""REPLAYGAIN_ALBUM_GAIN[=\x00\s]+([+-]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val trackPeakRegex = Regex("""REPLAYGAIN_TRACK_PEAK[=\x00\s]+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
    private val albumPeakRegex = Regex("""REPLAYGAIN_ALBUM_PEAK[=\x00\s]+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

    fun parseFromRawBytes(headerBytes: ByteArray): ParsedReplayGain {
        if (headerBytes.isEmpty()) return ParsedReplayGain()
        val textWindow = String(headerBytes, Charsets.ISO_8859_1)
        return parseFromText(textWindow)
    }

    fun parseFromText(rawText: String): ParsedReplayGain {
        val trackGain = trackGainRegex.find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val albumGain = albumGainRegex.find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val trackPeak = trackPeakRegex.find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        val albumPeak = albumPeakRegex.find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()

        return ParsedReplayGain(
            trackGainDb = trackGain?.takeIf { it in -40f..40f },
            albumGainDb = albumGain?.takeIf { it in -40f..40f },
            trackPeak = trackPeak?.takeIf { it > 0f && it <= 4f },
            albumPeak = albumPeak?.takeIf { it > 0f && it <= 4f }
        )
    }

    fun computeLinearMultiplier(
        gainDb: Float?,
        peak: Float?,
        preventClipping: Boolean = true
    ): Float {
        if (gainDb == null || gainDb.isNaN()) return 1.0f
        val clampedDb = gainDb.coerceIn(-30f, 24f)
        val rawLinear = 10.0.pow(clampedDb / 20.0).toFloat()
        if (preventClipping && peak != null && peak > 0f) {
            val maxAllowedLinear = 1.0f / peak
            return min(rawLinear, maxAllowedLinear)
        }
        return rawLinear
    }
}