package com.player.hifi.core.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.player.hifi.core.model.AudioOutputMode

@OptIn(UnstableApi::class)
object HiResAudioSinkProvider {

    fun createAudioSink(
        context: Context,
        outputMode: AudioOutputMode,
        processors: Array<AudioProcessor>,
        enablePlaybackParams: Boolean = false
    ): AudioSink {
        val useFloatHighResOutput = outputMode == AudioOutputMode.PRESERVE_SOURCE
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(useFloatHighResOutput)
            .setEnableAudioTrackPlaybackParams(enablePlaybackParams)
            .setAudioProcessors(processors)
            .build()
    }
}