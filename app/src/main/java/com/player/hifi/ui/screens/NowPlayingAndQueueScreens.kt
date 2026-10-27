package com.player.hifi.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.player.hifi.core.metadata.TrackLyrics
import com.player.hifi.ui.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingFullScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    val track by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val playbackPitch by viewModel.playbackPitch.collectAsStateWithLifecycle()
    val decodedInfo by viewModel.decodedAudioInfo.collectAsStateWithLifecycle()

    var showTechDetailsSheet by remember { mutableStateOf(false) }
    var showSpeedPitchSheet by remember { mutableStateOf(false) }
    var showLyricsView by remember { mutableStateOf(false) }
    var loadedLyrics by remember { mutableStateOf<TrackLyrics?>(null) }

    // Жесты строго по пальцу (1-к-1)
    val coverDragX = remember { Animatable(0f) }
    val screenDragY = remember { Animatable(0f) }

    val activeTrack = track ?: return
    val outputInfo = remember(activeTrack) { viewModel.currentOutputRouteInfo() }

    LaunchedEffect(activeTrack.id) {
        loadedLyrics = viewModel.loadLyrics(context.contentResolver, activeTrack)
    }

    val dragFractionY = (screenDragY.value / 900f).coerceIn(0f, 1f)
    val screenScale = 1f - (dragFractionY * 0.10f)
    val topCornerRadius = (dragFractionY * 32f).dp

    Surface(
        color = colors.background,
        contentColor = colors.onBackground,
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, screenDragY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = screenScale
                scaleY = screenScale
            }
            .clip(RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val nextY = (screenDragY.value + dragAmount).coerceAtLeast(0f)
                        scope.launch { screenDragY.snapTo(nextY) }
                    },
                    onDragEnd = {
                        if (screenDragY.value > 200f) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                screenDragY.snapTo(0f)
                                onCollapse()
                            }
                        } else {
                            scope.launch {
                                screenDragY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { screenDragY.animateTo(0f, spring()) }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. ЧИСТАЯ ВЕРХНЯЯ ПАНЕЛЬ В ЦВЕТАХ MD3
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onCollapse,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Collapse"
                    )
                }

                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onOpenEqualizer,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Equalizer"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { showTechDetailsSheet = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Audio Signal Path"
                        )
                    }
                }
            }

            // 2. СТРОГАЯ КВАДРАТНАЯ РАМКА ОБЛОЖКИ (СЛЕДУЕТ ЗА ПАЛЬЦЕМ 1-К-1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (showLyricsView) {
                    SyncedLyricsCard(
                        lyrics = loadedLyrics,
                        currentPositionMs = positionMs,
                        onSeekToLine = { timestampMs ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.seekTo(timestampMs)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val squareShape = RoundedCornerShape(24.dp)
                    Surface(
                        shape = squareShape,
                        color = colors.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = coverDragX.value
                                val scale = 1f - (abs(coverDragX.value) / 2800f).coerceIn(0f, 0.06f)
                                scaleX = scale
                                scaleY = scale
                            }
                            .border(
                                width = 1.5.dp,
                                color = colors.outlineVariant.copy(alpha = 0.6f),
                                shape = squareShape
                            )
                            .clip(squareShape)
                            .pointerInput(activeTrack.id) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            coverDragX.snapTo(
                                                (coverDragX.value + dragAmount).coerceIn(-420f, 420f)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        val finalX = coverDragX.value
                                        when {
                                            finalX < -120f -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.skipNext()
                                            }
                                            finalX > 120f -> {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.skipPrevious()
                                            }
                                        }
                                        scope.launch {
                                            coverDragX.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { coverDragX.animateTo(0f, spring()) }
                                    }
                                )
                            }
                            .pointerInput(activeTrack.id) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (tapOffset.x < size.width / 2f) {
                                            viewModel.seekByDelta(-10_000L)
                                        } else {
                                            viewModel.seekByDelta(10_000L)
                                        }
                                    },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTechDetailsSheet = true
                                    }
                                )
                            }
                    ) {
                        if (activeTrack.artworkUriString != null) {
                            AsyncImage(
                                model = activeTrack.artworkUriString,
                                contentDescription = "Album Artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Album,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(110.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. НАЗВАНИЕ ТРЕКА, АРТИСТ И ТЕХНИЧЕСКИЙ БЕЙДЖ (ПОД ОБЛОЖКОЙ)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTrack.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${activeTrack.artist} · ${activeTrack.album}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleFavorite(activeTrack)
                        }
                    ) {
                        Icon(
                            imageVector = if (activeTrack.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (activeTrack.isFavorite) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }

                // Техническая капсула MD3 (Format · Bitrate · Bit Depth · Sample Rate)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clickable { showTechDetailsSheet = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HighQuality,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeTrack.sourceMetadata.toPrimaryTechnicalLine(" · "),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // 4. ВОЛНИСТЫЙ ПРОГРЕСС-БАР И КАПСУЛЫ ВРЕМЕНИ В ЦВЕТАХ ТЕМЫ MD3
            val totalDuration = durationMs.takeIf { it > 0 } ?: activeTrack.durationMs.coerceAtLeast(1L)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                ) {
                    Text(
                        text = formatPlaybackDurationMs(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                TouchDrivenWavySeekBar(
                    progress = (positionMs.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f),
                    activeColor = colors.primary,
                    inactiveColor = colors.surfaceVariant,
                    onSeekFraction = { fraction ->
                        viewModel.seekTo((fraction * totalDuration).toLong())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                ) {
                    Text(
                        text = formatPlaybackDurationMs(totalDuration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 5. ГЛАВНЫЙ ДОК УПРАВЛЕНИЯ MD3 (БЕЗ ЛИШНИХ КНОПОК: ТОЛЬКО PREV, PLAY/PAUSE, NEXT)
            Surface(
                shape = RoundedCornerShape(50),
                color = colors.surfaceContainerHigh,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.skipPrevious()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.tertiaryContainer,
                            contentColor = colors.onTertiaryContainer
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                        shape = RoundedCornerShape(50),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier
                            .width(96.dp)
                            .height(58.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.skipNext()
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.tertiaryContainer,
                            contentColor = colors.onTertiaryContainer
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 6. КОМПАКТНЫЙ РЯД ПИЛЮЛЬ MD3 (Shuffle | Lyrics | Repeat | Queue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Md3ActionPill(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    isActive = shuffleEnabled,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleShuffle()
                    },
                    modifier = Modifier.weight(1f)
                )
                Md3ActionPill(
                    icon = Icons.Default.Lyrics,
                    label = "Lyrics",
                    isActive = showLyricsView,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLyricsView = !showLyricsView
                    },
                    modifier = Modifier.weight(1f)
                )
                Md3ActionPill(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    label = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> "Repeat 1"
                        Player.REPEAT_MODE_ALL -> "All"
                        else -> "Repeat"
                    },
                    isActive = repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.cycleRepeatMode()
                    },
                    modifier = Modifier.weight(1f)
                )
                Md3ActionPill(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Queue",
                    isActive = false,
                    onClick = onOpenQueue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showSpeedPitchSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedPitchSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed & Pitch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { viewModel.setPlaybackSpeedAndPitch(1.0f, 1.0f) }) {
                        Text("Reset 1.0x")
                    }
                }
                Text(String.format(Locale.US, "Speed: %.2fx", playbackSpeed))
                Slider(
                    value = playbackSpeed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.setPlaybackSpeedAndPitch(it, playbackPitch) }
                )
                Text(String.format(Locale.US, "Pitch: %.2fx", playbackPitch))
                Slider(
                    value = playbackPitch,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.setPlaybackSpeedAndPitch(playbackSpeed, it) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showTechDetailsSheet) {
        ModalBottomSheet(onDismissRequest = { showTechDetailsSheet = false }) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Signal Path & Metadata Inspection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Text("A. Source File Information (Original File)", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                val meta = activeTrack.sourceMetadata
                Text("Primary Technical Badge: ${meta.toPrimaryTechnicalLine(" · ")}", fontWeight = FontWeight.SemiBold)
                Text("Container / Codec: ${meta.containerFormat.label} / ${meta.codec.label}")
                Text("Bitrate: ${meta.formatBitrate()} (${meta.bitrateType.label})")
                Text("Source Bit Depth: ${meta.sourceBitDepth.formatForBadge()}")
                Text("Source Sample Rate: ${meta.formatSampleRate()}")
                Text("Channels: ${meta.channelCount ?: "Unknown"}")

                HorizontalDivider()
                Text("B. Decoded Audio Information (Media3 PCM Stream)", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                if (decodedInfo != null) {
                    Text("Active Decoder: ${decodedInfo!!.decoderName}")
                    Text("PCM Encoding: ${decodedInfo!!.pcmEncodingLabel}")
                    Text("Decoded Sample Rate: ${decodedInfo!!.decodedSampleRateHz / 1000.0} kHz (${decodedInfo!!.decodedChannelCount} ch)")
                } else {
                    Text("Start playback to inspect active decoder output.")
                }

                HorizontalDivider()
                Text("C. Output Path Information (Android Audio Route)", style = MaterialTheme.typography.titleSmall, color = colors.primary)
                Text("Active Route: ${outputInfo.routeName} (${outputInfo.routeTypeLabel})")
                Text("Selected Mode: ${outputInfo.requestedMode.label}")
                Text("Status: ${outputInfo.statusExplanation}", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = {
                        showTechDetailsSheet = false
                        showSpeedPitchSheet = true
                    }) {
                        Text("Speed & Pitch")
                    }
                    OutlinedButton(onClick = {
                        showTechDetailsSheet = false
                        onOpenSleepTimer()
                    }) {
                        Text("Sleep Timer")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TouchDrivenWavySeekBar(
    progress: Float,
    activeColor: androidx.compose.ui.graphics.Color,
    inactiveColor: androidx.compose.ui.graphics.Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress) }

    val activeFraction = if (isDragging) dragFraction else progress

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSeekFraction(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeekFraction(dragFraction)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeekFraction(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            }
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val thumbX = (activeFraction * width).coerceIn(0f, width)

        if (thumbX < width) {
            drawLine(
                color = inactiveColor,
                start = Offset(thumbX, centerY),
                end = Offset(width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        if (thumbX > 2f) {
            val wavePath = Path()
            val wavelengthPx = 28.dp.toPx()
            val amplitudePx = if (isDragging) 4.8.dp.toPx() else 3.2.dp.toPx()
            val fingerPhase = activeFraction * 18f

            wavePath.moveTo(0f, centerY + sin(fingerPhase) * amplitudePx)
            var x = 0f
            val step = 3f
            while (x <= thumbX) {
                val angle = ((x / wavelengthPx) * (2f * PI.toFloat())) + fingerPhase
                val y = centerY + sin(angle) * amplitudePx
                wavePath.lineTo(x, y)
                x += step
            }
            drawPath(
                path = wavePath,
                color = activeColor,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val thumbRadius = if (isDragging) 9.dp.toPx() else 7.dp.toPx()
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}

@Composable
private fun Md3ActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isActive) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (isActive) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SyncedLyricsCard(
    lyrics: TrackLyrics?,
    currentPositionMs: Long,
    onSeekToLine: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val colors = MaterialTheme.colorScheme

    val activeIndex = remember(lyrics, currentPositionMs) {
        if (lyrics == null || !lyrics.isSynced) -1
        else lyrics.lines.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        tonalElevation = 4.dp,
        modifier = modifier.border(
            width = 1.5.dp,
            color = colors.outlineVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(24.dp)
        )
    ) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "No embedded lyrics or companion .lrc file found for this track.\n\nTap Lyrics again to return to artwork.",
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(lyrics.lines) { idx, line ->
                    val isCurrent = lyrics.isSynced && idx == activeIndex
                    Text(
                        text = line.text,
                        fontSize = if (isCurrent) 20.sp else 16.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = lyrics.isSynced) {
                                onSeekToLine(line.timestampMs)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val queue by viewModel.queueTracks.collectAsStateWithLifecycle()
    val current by viewModel.currentTrack.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback Queue (${queue.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        onDismiss()
                    }
                ) {
                    Text("Clear Queue")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxHeight(0.65f)) {
                itemsIndexed(queue, key = { idx, t -> "${idx}_${t.id}" }) { index, track ->
                    val isCurrent = track.id == current?.id
                    ListItem(
                        headlineContent = {
                            Text(
                                text = if (isCurrent) "▶ ${track.title}" else track.title,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(track.sourceMetadata.toPrimaryTechnicalLine(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Row {
                                if (index > 0) {
                                    IconButton(onClick = { viewModel.moveQueueItem(index, index - 1) }) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                    }
                                }
                                if (index < queue.lastIndex) {
                                    IconButton(onClick = { viewModel.moveQueueItem(index, index + 1) }) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                    }
                                }
                                IconButton(onClick = { viewModel.removeQueueItem(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from Queue")
                                }
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .clickable { viewModel.playTracks(queue, index) }
                    )
                }
            }
        }
    }
}

fun formatPlaybackDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}