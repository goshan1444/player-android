package com.player.hifi

import com.player.hifi.core.metadata.ReplayGainParser
import com.player.hifi.core.model.AudioCodec
import com.player.hifi.core.model.BitDepthValue
import com.player.hifi.core.model.BitrateType
import com.player.hifi.core.model.ContainerFormat
import com.player.hifi.core.model.MetadataSource
import com.player.hifi.core.model.SourceAudioMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataAndBitrateTest {

    @Test
    fun `lossless 24-bit 96kHz FLAC formats primary technical line accurately`() {
        val meta = SourceAudioMetadata(
            containerFormat = ContainerFormat.FLAC,
            codec = AudioCodec.FLAC,
            sourceBitrateBps = 2_850_000,
            bitrateType = BitrateType.CBR,
            sourceBitDepth = BitDepthValue.Verified(24),
            sourceSampleRateHz = 96_000,
            channelCount = 2,
            durationMs = 240_000L,
            fileSizeBytes = 85_500_000L,
            metadataSource = MetadataSource.BINARY_HEADER_INSPECTION
        )

        assertEquals("FLAC · 2,850 kb/s · 24-bit · 96 kHz", meta.toPrimaryTechnicalLine())
    }

    @Test
    fun `lossy MP3 never fabricates bit depth and displays N_A`() {
        val meta = SourceAudioMetadata(
            containerFormat = ContainerFormat.MP3,
            codec = AudioCodec.MP3,
            sourceBitrateBps = 320_000,
            bitrateType = BitrateType.CBR,
            sourceBitDepth = BitDepthValue.NotApplicable,
            sourceSampleRateHz = 44_100,
            channelCount = 2,
            durationMs = 180_000L,
            fileSizeBytes = 7_200_000L,
            metadataSource = MetadataSource.MEDIA_EXTRACTOR_AND_TAGS
        )

        assertEquals("MP3 · 320 kb/s · N/A · 44.1 kHz", meta.toPrimaryTechnicalLine())
    }

    @Test
    fun `VBR bitrate is visually distinguished from exact CBR bitrate`() {
        val vbrMeta = SourceAudioMetadata(
            containerFormat = ContainerFormat.MP3,
            codec = AudioCodec.MP3,
            sourceBitrateBps = 245_000,
            bitrateType = BitrateType.VBR_ESTIMATED,
            sourceBitDepth = BitDepthValue.NotApplicable,
            sourceSampleRateHz = 48_000,
            channelCount = 2,
            durationMs = 200_000L,
            fileSizeBytes = 6_125_000L,
            metadataSource = MetadataSource.BINARY_HEADER_INSPECTION
        )

        assertEquals("MP3 · ~245 kb/s · N/A · 48 kHz", vbrMeta.toPrimaryTechnicalLine())
    }

    @Test
    fun `ReplayGain parser extracts tags and clamps positive gain to peak ceiling`() {
        val rawComment = "REPLAYGAIN_TRACK_GAIN=+6.0 dB\u0000REPLAYGAIN_TRACK_PEAK=0.8000"
        val parsed = ReplayGainParser.parseFromText(rawComment)

        assertEquals(6.0f, parsed.trackGainDb ?: 0f, 0.01f)
        assertEquals(0.8f, parsed.trackPeak ?: 0f, 0.001f)

        val linear = ReplayGainParser.computeLinearMultiplier(parsed.trackGainDb, parsed.trackPeak, preventClipping = true)
        assertTrue(linear <= 1.2501f)
    }
}