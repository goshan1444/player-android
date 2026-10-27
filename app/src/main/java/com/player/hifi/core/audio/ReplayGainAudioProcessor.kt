package com.player.hifi.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.player.hifi.core.metadata.ReplayGainParser
import com.player.hifi.core.model.ReplayGainMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class ReplayGainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var mode: ReplayGainMode = ReplayGainMode.OFF

    @Volatile
    private var trackGainDb: Float? = null

    @Volatile
    private var albumGainDb: Float? = null

    @Volatile
    private var trackPeak: Float? = null

    @Volatile
    private var albumPeak: Float? = null

    @Volatile
    var currentAppliedGainDb: Float? = null
        private set

    fun updateSettings(
        mode: ReplayGainMode,
        trackGainDb: Float?,
        albumGainDb: Float?,
        trackPeak: Float?,
        albumPeak: Float?
    ) {
        this.mode = mode
        this.trackGainDb = trackGainDb
        this.albumGainDb = albumGainDb
        this.trackPeak = trackPeak
        this.albumPeak = albumPeak
    }

    private fun resolveActiveLinearScale(): Float {
        val (selectedGain, selectedPeak) = when (mode) {
            ReplayGainMode.OFF -> null to null
            ReplayGainMode.TRACK -> (trackGainDb ?: albumGainDb) to (trackPeak ?: albumPeak)
            ReplayGainMode.ALBUM -> (albumGainDb ?: trackGainDb) to (albumPeak ?: trackPeak)
        }
        currentAppliedGainDb = selectedGain
        return ReplayGainParser.computeLinearMultiplier(selectedGain, selectedPeak, preventClipping = true)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> throw UnhandledAudioFormatException(inputAudioFormat)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val scale = resolveActiveLinearScale()
        val outputBuffer = replaceOutputBuffer(remaining).order(ByteOrder.nativeOrder())
        inputBuffer.order(ByteOrder.nativeOrder())

        if (scale == 1.0f) {
            outputBuffer.put(inputBuffer)
        } else {
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> {
                    while (inputBuffer.remaining() >= 2) {
                        val sample = inputBuffer.short
                        val scaled = (sample * scale).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        outputBuffer.putShort(scaled.toShort())
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    while (inputBuffer.remaining() >= 4) {
                        val sample = inputBuffer.float
                        val scaled = (sample * scale).coerceIn(-1.0f, 1.0f)
                        outputBuffer.putFloat(scaled)
                    }
                }
                else -> outputBuffer.put(inputBuffer)
            }
        }
        outputBuffer.flip()
    }
}