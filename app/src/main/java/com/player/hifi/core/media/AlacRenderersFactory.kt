package com.player.hifi.core.media

import android.content.Context
import android.media.MediaCodecList
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import com.player.hifi.core.audio.HiResAudioSinkProvider
import com.player.hifi.core.audio.ReplayGainAudioProcessor
import com.player.hifi.core.model.AudioOutputMode

@OptIn(UnstableApi::class)
class AlacRenderersFactory(
    context: Context,
    private val replayGainProcessor: ReplayGainAudioProcessor,
    private val outputModeProvider: () -> AudioOutputMode
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return HiResAudioSinkProvider.createAudioSink(
            context = context,
            outputMode = outputModeProvider(),
            processors = arrayOf(replayGainProcessor),
            enablePlaybackParams = enableAudioTrackPlaybackParams
        )
    }

    companion object {
        fun verifyAlacDecoderAvailability(): Pair<Boolean, String> {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val platformAlac = codecList.codecInfos.firstOrNull { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("audio/alac", ignoreCase = true) }
            }
            val ffmpegAvailable = runCatching {
                Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
                true
            }.getOrDefault(false)

            return when {
                platformAlac != null && ffmpegAvailable ->
                    true to "Verified: Platform (${platformAlac.name}) + FFmpeg ALAC extension"
                platformAlac != null ->
                    true to "Verified: Platform MediaCodec (${platformAlac.name})"
                ffmpegAvailable ->
                    true to "Verified: Bundled Media3 FFmpeg ALAC Decoder"
                else ->
                    false to "No ALAC decoder found"
            }
        }
    }
}