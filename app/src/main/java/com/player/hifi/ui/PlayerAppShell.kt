package com.player.hifi.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.player.hifi.core.model.Track
import com.player.hifi.ui.screens.EqualizerBottomSheet
import com.player.hifi.ui.screens.FoldersScreen
import com.player.hifi.ui.screens.HomeScreen
import com.player.hifi.ui.screens.LibraryScreen
import com.player.hifi.ui.screens.NowPlayingFullScreen
import com.player.hifi.ui.screens.PlaylistsScreen
import com.player.hifi.ui.screens.QueueBottomSheet
import com.player.hifi.ui.screens.SettingsScreen
import com.player.hifi.ui.screens.SleepTimerDialog
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class MainTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerAppShell(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { MainTab.entries.size }
    )

    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.rescanLibrary()
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addSafFolderAndScan(uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.rescanLibrary()
    }

    BackHandler(enabled = !showNowPlaying && pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    // Mini player — плавное появление/исчезновение снизу
                    AnimatedVisibility(
                        visible = currentTrack != null && !showNowPlaying,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeIn(tween(200)),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) + fadeOut(tween(150))
                    ) {
                        currentTrack?.let { track ->
                            val totalDur = durationMs.takeIf { it > 0 } ?: track.durationMs.coerceAtLeast(1L)
                            val progress = (positionMs.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f)
                            MiniPlayerBar(
                                track = track,
                                isPlaying = isPlaying,
                                progress = progress,
                                onTogglePlay = viewModel::togglePlayPause,
                                onSkipNext = viewModel::skipNext,
                                onExpand = { showNowPlaying = true }
                            )
                        }
                    }
                    NavigationBar {
                        MainTab.entries.forEachIndexed { index, tab ->
                            val isSelected = pagerState.currentPage == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            page = index,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            MainTab.HOME -> Icons.Default.Home
                                            MainTab.LIBRARY -> Icons.Default.LibraryMusic
                                            MainTab.FOLDERS -> Icons.Default.Folder
                                            MainTab.PLAYLISTS -> Icons.Default.QueueMusic
                                            MainTab.SETTINGS -> Icons.Default.Settings
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val fraction = 1f - abs(pageOffset)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = lerp(0.5f, 1f, fraction)
                                val scale = lerp(0.94f, 1f, fraction)
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        when (MainTab.entries[pageIndex]) {
                            MainTab.HOME -> HomeScreen(
                                viewModel = viewModel,
                                onRequestPermission = { permissionLauncher.launch(permissionToRequest) },
                                onPickSafFolder = { safFolderLauncher.launch(null) }
                            )
                            MainTab.LIBRARY -> LibraryScreen(viewModel = viewModel)
                            MainTab.FOLDERS -> FoldersScreen(
                                viewModel = viewModel,
                                onPickSafFolder = { safFolderLauncher.launch(null) }
                            )
                            MainTab.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                            MainTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onOpenEqualizer = { showEqualizerSheet = true },
                                onOpenSleepTimer = { showSleepTimerDialog = true }
                            )
                        }
                    }
                }

                playbackError?.let { errorMsg ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = viewModel::clearPlaybackError) {
                                Text("Dismiss")
                            }
                        }
                    ) {
                        Text(errorMsg)
                    }
                }
            }
        }

        // NowPlaying fullscreen overlay — плавный слайд вверх/вниз
        AnimatedVisibility(
            visible = showNowPlaying && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(tween(150)),
            exit = ExitTransition.None
        ) {
            NowPlayingFullScreen(
                viewModel = viewModel,
                onCollapse = { showNowPlaying = false },
                onOpenQueue = { showQueueSheet = true },
                onOpenEqualizer = { showEqualizerSheet = true },
                onOpenSleepTimer = { showSleepTimerDialog = true }
            )
        }
    }

    if (showQueueSheet) {
        QueueBottomSheet(viewModel = viewModel, onDismiss = { showQueueSheet = false })
    }
    if (showEqualizerSheet) {
        EqualizerBottomSheet(viewModel = viewModel, onDismiss = { showEqualizerSheet = false })
    }
    if (showSleepTimerDialog) {
        SleepTimerDialog(viewModel = viewModel, onDismiss = { showSleepTimerDialog = false })
    }
}

/**
 * Mini player bar — без свайп-жестов (они убраны по запросу).
 * Только кнопки Play/Pause, Next и клик для открытия NowPlaying.
 */
@Composable
private fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} · ${track.sourceMetadata.toPrimaryTechnicalLine()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next Track")
                }
            }
        }
    }
}
