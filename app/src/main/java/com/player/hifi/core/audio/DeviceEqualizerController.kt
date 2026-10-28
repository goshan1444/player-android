package com.player.hifi.core.audio

import android.media.audiofx.Equalizer
import com.player.hifi.core.model.EqualizerBandState
import com.player.hifi.core.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceEqualizerController {

    private var equalizer: Equalizer? = null
    private var boundSessionId: Int = 0

    private val _state = MutableStateFlow(
        EqualizerState(
            isSupportedByDevice = false,
            isEnabled = false,
            audioSessionId = 0,
            bands = emptyList(),
            presets = emptyList(),
            selectedPresetIndex = -1,
            errorReason = "Waiting for active audio session."
        )
    )
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun bindToAudioSession(
        audioSessionId: Int,
        initialEnabled: Boolean,
        savedBandLevels: Map<Short, Short>
    ) {
        if (audioSessionId <= 0) {
            release()
            _state.value = EqualizerState(
                isSupportedByDevice = false,
                isEnabled = false,
                audioSessionId = 0,
                bands = emptyList(),
                presets = emptyList(),
                selectedPresetIndex = -1,
                errorReason = "No active playback audio session."
            )
            return
        }
        if (boundSessionId == audioSessionId && equalizer != null) return

        release()
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            boundSessionId = audioSessionId

            val numBands = eq.numberOfBands
            val range = eq.bandLevelRange
            val minMb = range.getOrElse(0) { -1500 }
            val maxMb = range.getOrElse(1) { 1500 }

            savedBandLevels.forEach { (bandIndex, level) ->
                if (bandIndex in 0 until numBands) {
                    runCatching { eq.setBandLevel(bandIndex, level.coerceIn(minMb, maxMb)) }
                }
            }
            eq.enabled = initialEnabled

            refreshStateFromHardware()
        } catch (e: Exception) {
            release()
            _state.value = EqualizerState(
                isSupportedByDevice = false,
                isEnabled = false,
                audioSessionId = audioSessionId,
                bands = emptyList(),
                presets = emptyList(),
                selectedPresetIndex = -1,
                errorReason = "Equalizer unavailable: ${e.localizedMessage ?: "Hardware effect initialization failed"}"
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            refreshStateFromHardware()
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isEnabled = false,
                errorReason = "Could not toggle equalizer: ${e.localizedMessage}"
            )
        }
    }

    fun setBandLevel(bandIndex: Short, levelMillibels: Short): Map<Short, Short> {
        val eq = equalizer ?: return emptyMap()
        runCatching {
            if (!eq.enabled) {
                eq.enabled = true
            }
            eq.setBandLevel(bandIndex, levelMillibels)
            refreshStateFromHardware(selectedPreset = -1)
        }
        return currentBandMap()
    }

    fun applyPreset(presetIndex: Int): Map<Short, Short> {
        val eq = equalizer ?: return emptyMap()
        runCatching {
            if (presetIndex in 0 until eq.numberOfPresets) {
                if (!eq.enabled) {
                    eq.enabled = true
                }
                eq.usePreset(presetIndex.toShort())
                refreshStateFromHardware(selectedPreset = presetIndex)
            }
        }
        return currentBandMap()
    }

    fun resetFlat(): Map<Short, Short> {
        val eq = equalizer ?: return emptyMap()
        runCatching {
            for (b in 0 until eq.numberOfBands) {
                eq.setBandLevel(b.toShort(), 0)
            }
            refreshStateFromHardware(selectedPreset = -1)
        }
        return currentBandMap()
    }

    private fun currentBandMap(): Map<Short, Short> {
        return _state.value.bands.associate { it.index to it.currentLevelMillibels }
    }

    private fun refreshStateFromHardware(selectedPreset: Int = _state.value.selectedPresetIndex) {
        val eq = equalizer ?: return
        val range = eq.bandLevelRange
        val minMb = range.getOrElse(0) { -1500 }
        val maxMb = range.getOrElse(1) { 1500 }

        val bands = (0 until eq.numberOfBands).map { idx ->
            val shortIdx = idx.toShort()
            EqualizerBandState(
                index = shortIdx,
                centerFreqHz = eq.getCenterFreq(shortIdx) / 1000,
                currentLevelMillibels = eq.getBandLevel(shortIdx),
                minLevelMillibels = minMb,
                maxLevelMillibels = maxMb
            )
        }
        val presets = (0 until eq.numberOfPresets).map { idx ->
            eq.getPresetName(idx.toShort()).toString()
        }

        _state.value = EqualizerState(
            isSupportedByDevice = true,
            isEnabled = eq.enabled,
            audioSessionId = boundSessionId,
            bands = bands,
            presets = presets,
            selectedPresetIndex = selectedPreset,
            errorReason = null
        )
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        boundSessionId = 0
    }
}
