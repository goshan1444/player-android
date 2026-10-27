package com.player.hifi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.player.hifi.R
import com.player.hifi.core.model.SortOption
import com.player.hifi.core.model.Track
import com.player.hifi.ui.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    onRequestPermission: () -> Unit,
    onPickSafFolder: () -> Unit
) {
    val tracks by viewModel.sortedFilteredTracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val recentlyPlayed = remember(tracks) {
        tracks.filter { it.lastPlayedEpochMillis != null }
            .sortedByDescending { it.lastPlayedEpochMillis }
            .take(8)
    }
    val favorites = remember(tracks) { tracks.filter { it.isFavorite } }
    val recentlyAdded = remember(tracks) { tracks.sortedByDescending { it.dateAddedEpochSec }.take(10) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = viewModel::rescanLibrary) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan Library")
                }
            }
        }

        if (tracks.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Your Local Library is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.empty_library_message))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onRequestPermission) {
                            Text("Grant Audio Access")
                        }
                        OutlinedButton(onClick = onPickSafFolder) {
                            Text("Select Music Folder")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "hdr_recent") { HomeSectionHeader("Continue Listening") }
                    itemsIndexed(recentlyPlayed, key = { _, t -> "recent_${t.id}" }) { idx, track ->
                        Box(modifier = Modifier.animateItem()) {
                            SharedTrackRow(
                                track = track,
                                onClick = { viewModel.playTracks(recentlyPlayed, idx) },
                                onPlayNext = { viewModel.playNextInQueue(track) },
                                onToggleFavorite = { viewModel.toggleFavorite(track) }
                            )
                        }
                    }
                }
                if (favorites.isNotEmpty()) {
                    item(key = "hdr_fav") { HomeSectionHeader("Favorites (${favorites.size})") }
                    itemsIndexed(favorites.take(8), key = { _, t -> "fav_${t.id}" }) { idx, track ->
                        Box(modifier = Modifier.animateItem()) {
                            SharedTrackRow(
                                track = track,
                                onClick = { viewModel.playTracks(favorites, idx) },
                                onPlayNext = { viewModel.playNextInQueue(track) },
                                onToggleFavorite = { viewModel.toggleFavorite(track) }
                            )
                        }
                    }
                }
                item(key = "hdr_added") { HomeSectionHeader("Recently Added") }
                itemsIndexed(recentlyAdded, key = { _, t -> "added_${t.id}" }) { idx, track ->
                    Box(modifier = Modifier.animateItem()) {
                        SharedTrackRow(
                            track = track,
                            onClick = { viewModel.playTracks(recentlyAdded, idx) },
                            onPlayNext = { viewModel.playNextInQueue(track) },
                            onToggleFavorite = { viewModel.toggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(viewModel: PlayerViewModel) {
    val tracks by viewModel.sortedFilteredTracks.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val subPagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val tracksListState = rememberLazyListState()
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search tracks, albums, artists...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort Library")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.label}${if (settings.sortOption == option) " ✓" else ""}") },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            },
            singleLine = true
        )

        TabRow(
            selectedTabIndex = subPagerState.currentPage,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            val titles = listOf(
                "Tracks (${tracks.size})",
                "Albums (${albums.size})",
                "Artists (${artists.size})"
            )
            titles.forEachIndexed { idx, title ->
                Tab(
                    selected = subPagerState.currentPage == idx,
                    onClick = {
                        coroutineScope.launch {
                            subPagerState.animateScrollToPage(
                                page = idx,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    text = { Text(title) }
                )
            }
        }

        // Горизонтальное пролистывание подразделов Библиотеки (Tracks <-> Albums <-> Artists)
        HorizontalPager(
            state = subPagerState,
            modifier = Modifier.fillMaxSize()
        ) { subPage ->
            when (subPage) {
                0 -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = tracksListState,
                        contentPadding = PaddingValues(end = 26.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                            Box(modifier = Modifier.animateItem()) {
                                SharedTrackRow(
                                    track = track,
                                    onClick = { viewModel.playTracks(tracks, index) },
                                    onPlayNext = { viewModel.playNextInQueue(track) },
                                    onToggleFavorite = { viewModel.toggleFavorite(track) }
                                )
                            }
                        }
                    }

                    if (tracks.size > 12) {
                        AlphabetFastScroller(
                            tracks = tracks,
                            listState = tracksListState,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
                1 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(albums, key = { it.albumKey }) { album ->
                        ListItem(
                            headlineContent = { Text(album.title, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${album.artist} · ${album.trackCount} tracks") },
                            modifier = Modifier
                                .animateItem()
                                .clickable { viewModel.playTracks(album.tracks, 0) }
                        )
                    }
                }
                2 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(artists, key = { it.name }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text("${artist.albumCount} albums · ${artist.trackCount} tracks") },
                            modifier = Modifier
                                .animateItem()
                                .clickable { viewModel.playTracks(artist.tracks, 0) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetFastScroller(
    tracks: List<Track>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val letters = remember { ('A'..'Z').toList() + '#' }
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var barHeightPx by remember { mutableIntStateOf(1) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }

    fun scrollToLetterAtY(y: Float) {
        val fraction = (y / barHeightPx.toFloat()).coerceIn(0f, 0.999f)
        val index = (fraction * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val chosen = letters[index]
        if (activeLetter != chosen) {
            activeLetter = chosen
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val targetTrackIndex = tracks.indexOfFirst { track ->
                val firstChar = track.title.trim().firstOrNull()?.uppercaseChar() ?: '#'
                if (chosen == '#') !firstChar.isLetter() else firstChar >= chosen
            }
            if (targetTrackIndex >= 0) {
                coroutineScope.launch {
                    listState.scrollToItem(targetTrackIndex)
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxHeight()
    ) {
        AnimatedVisibility(
            visible = activeLetter != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = activeLetter?.toString().orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxHeight(0.88f)
                .width(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .onSizeChanged { barHeightPx = it.height.coerceAtLeast(1) }
                .pointerInput(tracks) {
                    detectTapGestures(
                        onPress = { offset ->
                            scrollToLetterAtY(offset.y)
                            tryAwaitRelease()
                            activeLetter = null
                        }
                    )
                }
                .pointerInput(tracks) {
                    detectDragGestures(
                        onDragStart = { offset -> scrollToLetterAtY(offset.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            scrollToLetterAtY(change.position.y)
                        },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null }
                    )
                }
                .padding(vertical = 4.dp)
        ) {
            letters.forEach { ch ->
                val isSelected = activeLetter == ch
                Text(
                    text = ch.toString(),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTrackRow(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayNext()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                        Text("Play Next", fontWeight = FontWeight.Bold)
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (track.isFavorite) "Unfavorite" else "Favorite", fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Favorite, contentDescription = null)
                    }
                }
            }
        }
    ) {
        ListItem(
            headlineContent = {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                Text(
                    text = "${track.artist} · ${track.sourceMetadata.toPrimaryTechnicalLine()}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                IconButton(onClick = onPlayNext) {
                    Icon(Icons.Default.PlaylistPlay, contentDescription = "Play Next")
                }
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}