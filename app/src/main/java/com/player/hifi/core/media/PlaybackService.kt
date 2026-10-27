package com.player.hifi.core.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.player.hifi.MainActivity
import com.player.hifi.PlayerApp
import com.player.hifi.core.model.DecodedAudioInfo

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var exoPlayer: ExoPlayer

    override fun onCreate() {
        super.onCreate()
        val appContainer = (application as PlayerApp).container

        val renderersFactory = AlacRenderersFactory(
            context = this,
            replayGainProcessor = appContainer.replayGainProcessor,
            outputModeProvider = { appContainer.currentAudioOutputMode }
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        exoPlayer.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: androidx.media3.common.Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                val pcmLabel = when (format.pcmEncoding) {
                    C.ENCODING_PCM_16BIT -> "16-bit Integer PCM"
                    C.ENCODING_PCM_24BIT -> "24-bit Integer PCM"
                    C.ENCODING_PCM_32BIT -> "32-bit Integer PCM"
                    C.ENCODING_PCM_FLOAT -> "32-bit Float PCM"
                    else -> "Decoded PCM Stream"
                }
                appContainer.updateDecodedAudioInfo(
                    DecodedAudioInfo(
                        decoderName = decoderReuseEvaluation?.decoderName ?: "Media3 / Platform Audio Decoder",
                        pcmEncodingLabel = pcmLabel,
                        decodedSampleRateHz = format.sampleRate.takeIf { it > 0 } ?: 44100,
                        decodedChannelCount = format.channelCount.takeIf { it > 0 } ?: 2
                    )
                )
            }

            override fun onAudioSessionIdChanged(
                eventTime: AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                appContainer.onAudioSessionIdChanged(audioSessionId)
            }
        })

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                appContainer.onActiveMediaItemChanged(mediaItem?.mediaId)
            }

            override fun onPlayerError(error: PlaybackException) {
                appContainer.onPlaybackError(
                    "Unable to play this file (${error.errorCodeName}). The audio format may be unsupported or the file may be damaged."
                )
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()

        appContainer.attachServicePlayer(exoPlayer)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}