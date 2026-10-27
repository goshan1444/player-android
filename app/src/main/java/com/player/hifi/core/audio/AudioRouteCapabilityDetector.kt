package com.player.hifi.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.player.hifi.core.model.AudioOutputMode
import com.player.hifi.core.model.OutputRouteInfo

class AudioRouteCapabilityDetector(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun inspectCurrentOutputRoute(requestedMode: AudioOutputMode): OutputRouteInfo {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        ?: devices.firstOrNull()

        if (activeDevice == null) {
            return OutputRouteInfo(
                routeName = "Android Default Output",
                routeTypeLabel = "Unknown Route",
                requestedMode = requestedMode,
                isHiResDirectOrFloatActive = false,
                verifiedSinkSampleRatesHz = emptyList(),
                statusExplanation = "Output device capabilities could not be queried from AudioManager."
            )
        }

        val typeLabel = when (activeDevice.type) {
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio DAC / Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            else -> "System Audio Output"
        }

        val supportedRates = activeDevice.sampleRates.sorted()
        val supportsHighSampleRate = supportedRates.any { it >= 88200 } || activeDevice.type == AudioDeviceInfo.TYPE_USB_HEADSET
        val hiResActive = requestedMode == AudioOutputMode.PRESERVE_SOURCE &&
                activeDevice.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

        val explanation = when {
            requestedMode == AudioOutputMode.STANDARD ->
                "Standard 16-bit PCM Android mixer path active."
            activeDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ->
                "High-Resolution mode requested, but Bluetooth A2DP re-encodes audio over wireless transport."
            hiResActive && supportsHighSampleRate ->
                "High-Resolution Float PCM sink active. Route reports support up to ${supportedRates.maxOrNull()?.div(1000) ?: 48} kHz (Android OS mixer may still resample unless exclusive USB mode is negotiated by platform)."
            hiResActive ->
                "High-Resolution Float PCM sink active on $typeLabel. Native sample rates reported: ${if (supportedRates.isEmpty()) "System Managed (48 kHz typical)" else supportedRates.joinToString { "${it / 1000.0} kHz" }}."
            else ->
                "Standard Android mixer path active."
        }

        return OutputRouteInfo(
            routeName = activeDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel,
            routeTypeLabel = typeLabel,
            requestedMode = requestedMode,
            isHiResDirectOrFloatActive = hiResActive,
            verifiedSinkSampleRatesHz = supportedRates,
            statusExplanation = explanation
        )
    }
}