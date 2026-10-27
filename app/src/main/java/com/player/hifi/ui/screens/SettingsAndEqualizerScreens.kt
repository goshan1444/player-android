package com.player.hifi.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.player.hifi.BuildConfig
import com.player.hifi.core.model.AudioOutputMode
import com.player.hifi.core.model.ReplayGainMode
import com.player.hifi.core.model.SortOption
import com.player.hifi.core.model.ThemeMode
import com.player.hifi.ui.PlayerViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: PlayerViewModel,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sleepRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    val outputInfo = remember(settings.audioOutputMode) { viewModel.currentOutputRouteInfo() }
    var showLicensesDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item { SettingsSectionTitle("Appearance") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    SettingsSwitchItem(
                        title = "AMOLED True-Black Dark Theme",
                        subtitle = "Forces #000000 pure black surfaces when Dark Mode is active. Dynamic Color will not override the black background.",
                        checked = settings.useAmoledDark,
                        onCheckedChange = viewModel::setUseAmoledDark
                    )

                    SettingsSwitchItem(
                        title = "Dynamic Color (Material You)",
                        subtitle = "Adapts accent colors to your Pixel wallpaper on Android 12+.",
                        checked = settings.useDynamicColor,
                        onCheckedChange = viewModel::setUseDynamicColor
                    )
                }
            }
        }

        item { SettingsSectionTitle("Playback & Audio Engine") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingsSwitchItem(
                        title = "Gapless Playback",
                        subtitle = "Pre-buffers next track in Media3 pipeline for seamless album transitions.",
                        checked = settings.gaplessPlaybackEnabled,
                        onCheckedChange = viewModel::setGaplessEnabled
                    )

                    HorizontalDivider()

                    Text(
                        text = "ReplayGain Normalization",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Applies lossless PCM scaling with peak clipping protection. Never modifies source files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReplayGainMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.replayGainMode == mode,
                                onClick = { viewModel.setReplayGainMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Audio Output Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioOutputMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.audioOutputMode == mode,
                                onClick = { viewModel.setAudioOutputMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Text(
                        text = outputInfo.statusExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenEqualizer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Equalizer")
                        }
                        OutlinedButton(
                            onClick = onOpenSleepTimer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bedtime, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (sleepRemainingMs != null) {
                                    "Timer (${formatTimerMs(sleepRemainingMs ?: 0L)})"
                                } else {
                                    "Sleep Timer"
                                }
                            )
                        }
                    }
                }
            }
        }

        item { SettingsSectionTitle("Library & Storage") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Library Scanner Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isScanning) "Scanning local files & binary headers..." else "Idle · Ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = viewModel::rescanLibrary,
                            enabled = !isScanning
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Rescan Now")
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = "Default Track Sorting",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SortOption.entries.take(3).forEach { opt ->
                            FilterChip(
                                selected = settings.sortOption == opt,
                                onClick = { viewModel.setSortOption(opt) },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SortOption.entries.drop(3).forEach { opt ->
                            FilterChip(
                                selected = settings.sortOption == opt,
                                onClick = { viewModel.setSortOption(opt) },
                                label = { Text(opt.label) }
                            )
                        }
                    }

                    if (settings.excludedFolders.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Excluded Folders (${settings.excludedFolders.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        settings.excludedFolders.forEach { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(folder, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.toggleExcludeFolder(folder) }) {
                                    Text("Include")
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SettingsSectionTitle("About & Audio Diagnostics") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Player v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "ALAC Decoder: ${viewModel.alacSupportReport.second}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Active Audio Route: ${outputInfo.routeName} (${outputInfo.routeTypeLabel})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Reported Route Sample Rates: ${
                            if (outputInfo.verifiedSinkSampleRatesHz.isEmpty()) "System Managed (48 kHz default)"
                            else outputInfo.verifiedSinkSampleRatesHz.joinToString { "${it / 1000.0} kHz" }
                        }",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { showLicensesDialog = true }) {
                        Text("Open-Source Licenses")
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Licenses") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• AndroidX Media3, Jetpack Compose, Room, DataStore, Kotlin Coroutines: Apache License 2.0")
                    Text("• Jellyfin Media3 FFmpeg Audio Extension (org.jellyfin.media3:media3-ffmpeg-decoder): LGPLv2.1+ (Audio decoders only)")
                    Text("• Coil Image Loader: Apache License 2.0")
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware Equalizer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (eqState.audioSessionId > 0) "Bound to AudioSession #${eqState.audioSessionId}" else "Inactive Session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (eqState.isSupportedByDevice) {
                    Switch(
                        checked = eqState.isEnabled,
                        onCheckedChange = viewModel::setEqualizerEnabled
                    )
                }
            }

            if (!eqState.isSupportedByDevice) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = eqState.errorReason
                            ?: "Equalizer effect is not supported on the active audio session or output route. Start playback first or switch audio routes.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = viewModel::resetEqualizerFlat) {
                        Text("Reset (0 dB)")
                    }
                    eqState.presets.take(3).forEachIndexed { index, presetName ->
                        FilterChip(
                            selected = eqState.selectedPresetIndex == index,
                            onClick = { viewModel.applyEqualizerPreset(index) },
                            label = { Text(presetName) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(eqState.bands, key = { it.index }) { band ->
                        val freqLabel = if (band.centerFreqHz >= 1000) {
                            String.format(Locale.US, "%.1f kHz", band.centerFreqHz / 1000f)
                        } else {
                            "${band.centerFreqHz} Hz"
                        }
                        val dbValue = band.currentLevelMillibels / 100f
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(freqLabel, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = String.format(Locale.US, "%+.1f dB", dbValue),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Slider(
                                value = band.currentLevelMillibels.toFloat(),
                                valueRange = band.minLevelMillibels.toFloat()..band.maxLevelMillibels.toFloat(),
                                enabled = eqState.isEnabled,
                                onValueChange = { newValue ->
                                    viewModel.setEqualizerBand(band.index, newValue.toInt().toShort())
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SleepTimerDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val remainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    var customMinutes by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (remainingMs != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Playback will stop in ${formatTimerMs(remainingMs ?: 0L)}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Select a preset duration or enter custom minutes. The timer runs inside the playback service and survives screen-off.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilledTonalButton(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("${mins}m")
                        }
                    }
                }

                OutlinedTextField(
                    value = customMinutes,
                    onValueChange = { input -> customMinutes = input.filter { it.isDigit() }.take(3) },
                    label = { Text("Custom duration (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = (customMinutes.toIntOrNull() ?: 0) > 0,
                onClick = {
                    customMinutes.toIntOrNull()?.let { viewModel.startSleepTimer(it) }
                    onDismiss()
                }
            ) {
                Text("Start Custom")
            }
        },
        dismissButton = {
            Row {
                if (remainingMs != null) {
                    TextButton(
                        onClick = {
                            viewModel.cancelSleepTimer()
                            onDismiss()
                        }
                    ) {
                        Text("Cancel Timer")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun formatTimerMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}