package com.player.hifi

import com.player.hifi.core.metadata.ReplayGainParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainAndSleepTimerTest {

    @Test
    fun `missing ReplayGain tags return neutral 1_0x multiplier without modifying audio`() {
        val parsed = ReplayGainParser.parseFromText("TITLE=Test Track\u0000ARTIST=Test Artist")
        assertNull(parsed.trackGainDb)
        assertNull(parsed.albumGainDb)

        val multiplier = ReplayGainParser.computeLinearMultiplier(
            gainDb = parsed.trackGainDb,
            peak = parsed.trackPeak,
            preventClipping = true
        )
        assertEquals(1.0f, multiplier, 0.0001f)
    }

    @Test
    fun `negative ReplayGain attenuates signal accurately`() {
        val parsed = ReplayGainParser.parseFromText("REPLAYGAIN_ALBUM_GAIN=-6.02 dB\u0000REPLAYGAIN_ALBUM_PEAK=0.98")
        assertEquals(-6.02f, parsed.albumGainDb ?: 0f, 0.01f)

        val multiplier = ReplayGainParser.computeLinearMultiplier(
            gainDb = parsed.albumGainDb,
            peak = parsed.albumPeak,
            preventClipping = true
        )
        assertEquals(0.50f, multiplier, 0.01f)
    }

    @Test
    fun `corrupt or out-of-range ReplayGain values are rejected safely`() {
        val corrupt = ReplayGainParser.parseFromText("REPLAYGAIN_TRACK_GAIN=+999.0 dB\u0000REPLAYGAIN_TRACK_PEAK=-2.0")
        assertNull(corrupt.trackGainDb)
        assertNull(corrupt.trackPeak)
    }
}