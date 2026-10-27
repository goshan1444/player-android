package com.player.hifi.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.player.hifi.core.model.AudioOutputMode
import com.player.hifi.core.model.ReplayGainMode
import com.player.hifi.core.model.SortOption
import com.player.hifi.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "player_preferences")

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useAmoledDark: Boolean = true,
    val useDynamicColor: Boolean = true,
    val gaplessPlaybackEnabled: Boolean = true,
    val replayGainMode: ReplayGainMode = ReplayGainMode.OFF,
    val audioOutputMode: AudioOutputMode = AudioOutputMode.STANDARD,
    val equalizerEnabled: Boolean = false,
    val equalizerBandsMillibels: Map<Short, Short> = emptyMap(),
    val sortOption: SortOption = SortOption.TITLE,
    val excludedFolders: Set<String> = emptySet(),
    val authorizedSafTreeUris: Set<String> = emptySet(),
    val sleepTimerDeadlineEpochMs: Long = 0L,
    val lastQueueTrackIds: List<String> = emptyList(),
    val lastPlayingTrackId: String? = null,
    val lastPlaybackPositionMs: Long = 0L
)

class PlayerPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_AMOLED_DARK = booleanPreferencesKey("use_amoled_dark")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        val AUDIO_OUTPUT_MODE = stringPreferencesKey("audio_output_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val SAF_TREE_URIS = stringSetPreferencesKey("saf_tree_uris")
        val SLEEP_TIMER_DEADLINE = longPreferencesKey("sleep_timer_deadline_epoch_ms")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            useAmoledDark = prefs[Keys.USE_AMOLED_DARK] ?: true,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
            replayGainMode = prefs[Keys.REPLAY_GAIN_MODE]?.let { runCatching { ReplayGainMode.valueOf(it) }.getOrNull() } ?: ReplayGainMode.OFF,
            audioOutputMode = prefs[Keys.AUDIO_OUTPUT_MODE]?.let { runCatching { AudioOutputMode.valueOf(it) }.getOrNull() } ?: AudioOutputMode.STANDARD,
            equalizerEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            equalizerBandsMillibels = deserializeBands(prefs[Keys.EQ_BANDS].orEmpty()),
            sortOption = prefs[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() } ?: SortOption.TITLE,
            excludedFolders = prefs[Keys.EXCLUDED_FOLDERS] ?: emptySet(),
            authorizedSafTreeUris = prefs[Keys.SAF_TREE_URIS] ?: emptySet(),
            sleepTimerDeadlineEpochMs = prefs[Keys.SLEEP_TIMER_DEADLINE] ?: 0L,
            lastQueueTrackIds = prefs[Keys.LAST_QUEUE_IDS]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
            lastPlayingTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPlaybackPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setUseAmoledDark(enabled: Boolean) = context.dataStore.edit { it[Keys.USE_AMOLED_DARK] = enabled }
    suspend fun setUseDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    suspend fun setGaplessEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.GAPLESS_ENABLED] = enabled }
    suspend fun setReplayGainMode(mode: ReplayGainMode) = context.dataStore.edit { it[Keys.REPLAY_GAIN_MODE] = mode.name }
    suspend fun setAudioOutputMode(mode: AudioOutputMode) = context.dataStore.edit { it[Keys.AUDIO_OUTPUT_MODE] = mode.name }
    suspend fun setSortOption(option: SortOption) = context.dataStore.edit { it[Keys.SORT_OPTION] = option.name }

    suspend fun saveEqualizerState(enabled: Boolean, bands: Map<Short, Short>) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_BANDS] = bands.entries.joinToString(",") { (k, v) -> "$k:$v" }
        }
    }

    suspend fun toggleExcludedFolder(folderPath: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXCLUDED_FOLDERS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(folderPath)) current.remove(folderPath)
            prefs[Keys.EXCLUDED_FOLDERS] = current
        }
    }

    suspend fun addSafTreeUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SAF_TREE_URIS]?.toMutableSet() ?: mutableSetOf()
            current.add(uriString)
            prefs[Keys.SAF_TREE_URIS] = current
        }
    }

    suspend fun setSleepTimerDeadline(deadlineEpochMs: Long) {
        context.dataStore.edit { it[Keys.SLEEP_TIMER_DEADLINE] = deadlineEpochMs }
    }

    suspend fun savePlaybackSessionState(queueIds: List<String>, activeTrackId: String?, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_QUEUE_IDS] = queueIds.joinToString("|")
            if (activeTrackId != null) it[Keys.LAST_TRACK_ID] = activeTrackId else it.remove(Keys.LAST_TRACK_ID)
            it[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    private fun deserializeBands(raw: String): Map<Short, Short> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val k = parts[0].toShortOrNull()
                val v = parts[1].toShortOrNull()
                if (k != null && v != null) k to v else null
            } else null
        }.toMap()
    }
}