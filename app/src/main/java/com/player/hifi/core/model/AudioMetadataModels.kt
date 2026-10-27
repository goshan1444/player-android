package com.player.hifi.core.model

import java.util.Locale

enum class ContainerFormat(val label: String) {
    MP3("MP3"),
    M4A("M4A"),
    FLAC("FLAC"),
    WAV("WAV"),
    UNKNOWN("Unknown")
}

enum class AudioCodec(val label: String, val isLossless: Boolean) {
    MP3("MP3", isLossless = false),
    AAC("AAC", isLossless = false),
    ALAC("ALAC", isLossless = true),
    FLAC("FLAC", isLossless = true),
    PCM("PCM", isLossless = true),
    UNKNOWN("Unknown", isLossless = false)
}

enum class BitrateType(val label: String) {
    CBR("CBR (Declared)"),
    VBR_ESTIMATED("VBR (Estimated)"),
    PCM_EXACT("Exact Stream"),
    UNKNOWN("Unknown")
}

sealed class BitDepthValue {
    data class Verified(val bits: Int) : BitDepthValue()
    data object NotApplicable : BitDepthValue()
    data object Unknown : BitDepthValue()

    fun formatForBadge(): String = when (this) {
        is Verified -> "$bits-bit"
        is NotApplicable -> "N/A"
        is Unknown -> "Unknown"
    }
}

enum class MetadataSource {
    BINARY_HEADER_INSPECTION,
    MEDIA_EXTRACTOR_AND_TAGS,
    FALLBACK_MEDIASTORE
}

data class SourceAudioMetadata(
    val containerFormat: ContainerFormat,
    val codec: AudioCodec,
    val sourceBitrateBps: Int?,
    val bitrateType: BitrateType,
    val sourceBitDepth: BitDepthValue,
    val sourceSampleRateHz: Int?,
    val channelCount: Int?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val replayGainTrackDb: Float? = null,
    val replayGainAlbumDb: Float? = null,
    val replayGainTrackPeak: Float? = null,
    val replayGainAlbumPeak: Float? = null,
    val metadataSource: MetadataSource
) {
    fun formatCodecBadge(): String = when {
        containerFormat == ContainerFormat.M4A && codec == AudioCodec.ALAC -> "ALAC (M4A)"
        containerFormat == ContainerFormat.M4A && codec == AudioCodec.AAC -> "AAC (M4A)"
        containerFormat == ContainerFormat.WAV && codec == AudioCodec.PCM -> "WAV PCM"
        codec != AudioCodec.UNKNOWN -> codec.label
        else -> containerFormat.label
    }

    fun formatBitrate(): String {
        val bps = sourceBitrateBps ?: return "Unknown"
        if (bps <= 0) return "Unknown"
        val kbps = bps / 1000
        val prefix = if (bitrateType == BitrateType.VBR_ESTIMATED) "~" else ""
        return String.format(Locale.US, "%s%,d kb/s", prefix, kbps)
    }

    fun formatSampleRate(): String {
        val hz = sourceSampleRateHz ?: return "Unknown"
        if (hz <= 0) return "Unknown"
        val khz = hz / 1000.0
        return if (hz % 1000 == 0) {
            String.format(Locale.US, "%d kHz", hz / 1000)
        } else {
            String.format(Locale.US, "%.1f kHz", khz)
        }
    }

    fun toPrimaryTechnicalLine(separator: String = " · "): String {
        return listOf(
            formatCodecBadge(),
            formatBitrate(),
            sourceBitDepth.formatForBadge(),
            formatSampleRate()
        ).joinToString(separator)
    }
}

data class DecodedAudioInfo(
    val decoderName: String,
    val pcmEncodingLabel: String,
    val decodedSampleRateHz: Int,
    val decodedChannelCount: Int
)

data class OutputRouteInfo(
    val routeName: String,
    val routeTypeLabel: String,
    val requestedMode: AudioOutputMode,
    val isHiResDirectOrFloatActive: Boolean,
    val verifiedSinkSampleRatesHz: List<Int>,
    val statusExplanation: String
)

enum class AudioOutputMode(val label: String, val description: String) {
    STANDARD(
        "Standard Android Output",
        "Uses standard 16-bit PCM mixer path for maximum compatibility and system audio effects."
    ),
    PRESERVE_SOURCE(
        "High-Resolution / Preserve Source",
        "Requests high-bit-depth float PCM output and attempts to preserve source sample rate where hardware allows."
    )
}

enum class ReplayGainMode(val label: String) {
    OFF("Off"),
    TRACK("Track Gain"),
    ALBUM("Album Gain")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Date Added"),
    FILENAME("Filename")
}