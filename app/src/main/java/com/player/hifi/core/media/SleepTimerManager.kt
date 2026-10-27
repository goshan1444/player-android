package com.player.hifi.core.media

import com.player.hifi.core.datastore.PlayerPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SleepTimerManager(
    private val scope: CoroutineScope,
    private val preferences: PlayerPreferencesRepository,
    private val onVolumeScaleChanged: (Float) -> Unit = {},
    private val onTimerExpired: () -> Unit,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private var countdownJob: Job? = null

    private val _remainingMillis = MutableStateFlow<Long?>(null)
    val remainingMillis: StateFlow<Long?> = _remainingMillis.asStateFlow()

    init {
        scope.launch {
            val savedDeadline = preferences.settingsFlow.first().sleepTimerDeadlineEpochMs
            if (savedDeadline > 0L) {
                val remaining = savedDeadline - clockMillis()
                if (remaining <= 0L) {
                    preferences.setSleepTimerDeadline(0L)
                    withContext(Dispatchers.Main) {
                        onVolumeScaleChanged(1.0f)
                        onTimerExpired()
                    }
                } else {
                    startTicker(savedDeadline)
                }
            }
        }
    }

    fun startTimer(durationMinutes: Int) {
        if (durationMinutes <= 0) {
            cancelTimer()
            return
        }
        val deadline = clockMillis() + (durationMinutes * 60_000L)
        scope.launch {
            preferences.setSleepTimerDeadline(deadline)
        }
        startTicker(deadline)
    }

    fun cancelTimer() {
        countdownJob?.cancel()
        countdownJob = null
        _remainingMillis.value = null
        onVolumeScaleChanged(1.0f)
        scope.launch {
            preferences.setSleepTimerDeadline(0L)
        }
    }

    private fun startTicker(deadlineEpochMs: Long) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            while (isActive) {
                val remaining = deadlineEpochMs - clockMillis()
                if (remaining <= 0L) {
                    _remainingMillis.value = null
                    preferences.setSleepTimerDeadline(0L)
                    withContext(Dispatchers.Main) {
                        onTimerExpired()
                        // Восстанавливаем полную громкость после паузы для следующего запуска
                        onVolumeScaleChanged(1.0f)
                    }
                    break
                } else {
                    _remainingMillis.value = remaining
                    // Плавный Fade-Out за последние 10 секунд
                    val fadeWindowMs = 10_000L
                    if (remaining in 1..fadeWindowMs) {
                        val volumeScale = (remaining.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.05f, 1.0f)
                        withContext(Dispatchers.Main) {
                            onVolumeScaleChanged(volumeScale)
                        }
                    }
                    delay(250L)
                }
            }
        }
    }
}