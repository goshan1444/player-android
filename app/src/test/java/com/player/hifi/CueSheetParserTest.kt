package com.player.hifi

import com.player.hifi.core.metadata.CueSheetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CueSheetParserTest {

    @Test
    fun `parses standard EAC CUE sheet accurately`() {
        val cueText = """
            REM GENRE Synthpop
            REM DATE 2012
            PERFORMER "Owl City"
            TITLE "The Midsummer Station"
            FILE "Owl City - The Midsummer Station.wav" WAVE
              TRACK 01 AUDIO
                TITLE "Dreams And Disasters"
                PERFORMER "Owl City"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "Shooting Star"
                PERFORMER "Owl City"
                INDEX 01 03:45:00
              TRACK 03 AUDIO
                TITLE "Gold"
                PERFORMER "Owl City"
                INDEX 01 07:52:45
        """.trimIndent()

        val result = CueSheetParser.parseCueText(cueText)
        assertNotNull(result)
        assertEquals("The Midsummer Station", result!!.albumTitle)
        assertEquals("Owl City", result.albumPerformer)
        assertEquals("Owl City - The Midsummer Station.wav", result.targetAudioFileName)
        assertEquals(3, result.tracks.size)

        // Track 1
        assertEquals(1, result.tracks[0].trackNumber)
        assertEquals("Dreams And Disasters", result.tracks[0].title)
        assertEquals(0L, result.tracks[0].startPositionMs)
        assertEquals(225_000L, result.tracks[0].endPositionMs) // 3m 45s = 225,000 ms

        // Track 2
        assertEquals(2, result.tracks[1].trackNumber)
        assertEquals("Shooting Star", result.tracks[1].title)
        assertEquals(225_000L, result.tracks[1].startPositionMs)

        // Track 3 (last track, end is null)
        assertEquals(3, result.tracks[2].trackNumber)
        assertEquals(null, result.tracks[2].endPositionMs)
    }

    @Test
    fun `parses CUE sheet without quotes and with Cyrillic Windows-1251 encoding`() {
        val rawCue = """
            PERFORMER Наш Исполнитель
            TITLE Наш Альбом
            FILE audio.flac WAVE
              TRACK 1 AUDIO
                TITLE Первый Трек
                INDEX 01 00:00:00
              TRACK 2 AUDIO
                TITLE Второй Трек
                INDEX 01 04:12:00
        """.trimIndent()

        val cp1251 = Charset.forName("windows-1251")
        val bytes = rawCue.toByteArray(cp1251)

        val result = CueSheetParser.parseCueBytes(bytes)
        assertNotNull(result)
        assertEquals("Наш Альбом", result!!.albumTitle)
        assertEquals("Наш Исполнитель", result.albumPerformer)
        assertEquals("audio.flac", result.targetAudioFileName)
        assertEquals(2, result.tracks.size)
        assertEquals("Первый Трек", result.tracks[0].title)
        assertEquals("Второй Трек", result.tracks[1].title)
    }

    @Test
    fun `handles UTF-8 with BOM correctly`() {
        val cueText = "TITLE \"Test Album\"\nFILE \"test.flac\" WAVE\nTRACK 01 AUDIO\nTITLE \"Track 1\"\nINDEX 01 00:00:00"
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + cueText.toByteArray(Charsets.UTF_8)

        val result = CueSheetParser.parseCueBytes(bytes)
        assertNotNull(result)
        assertEquals("Test Album", result!!.albumTitle)
        assertEquals(1, result.tracks.size)
        assertEquals("Track 1", result.tracks[0].title)
    }
}
