package com.player.hifi.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.player.hifi.core.model.PlaylistWithTracks
import com.player.hifi.core.model.Track
import com.player.hifi.ui.PlayerViewModel

// ─── Folders ────────────────────────────────────────────────────────────────

@Composable
fun FoldersScreen(
    viewModel: PlayerViewModel,
    onPickSafFolder: () -> Unit
) {
    val folderMap by viewModel.folderMap.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Navigation state: null = folder list, non-null = folder detail
    var openedFolderPath by remember { mutableStateOf<String?>(null) }

    AnimatedContent(
        targetState = openedFolderPath,
        transitionSpec = {
            if (targetState != null) {
                // Drill into folder
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200)))
            } else {
                // Back to list
                (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
            }
        },
        label = "FolderNavigation"
    ) { currentFolder ->
        if (currentFolder == null) {
            // ── Folder list screen ──────────────────────────────────────────
            FolderListScreen(
                folderMap = folderMap,
                excludedFolders = settings.excludedFolders,
                onPickSafFolder = onPickSafFolder,
                onOpenFolder = { openedFolderPath = it },
                onExcludeFolder = viewModel::toggleExcludeFolder
            )
        } else {
            // ── Folder detail screen ────────────────────────────────────────
            val tracks = folderMap[currentFolder] ?: emptyList()
            FolderDetailScreen(
                folderPath = currentFolder,
                tracks = tracks,
                onBack = { openedFolderPath = null },
                onPlayFolder = { viewModel.playTracks(tracks, 0) },
                onPlayTrack = { idx -> viewModel.playTracks(tracks, idx) },
                onPlayNext = { track -> viewModel.playNextInQueue(track) },
                onExcludeFolder = {
                    viewModel.toggleExcludeFolder(currentFolder)
                    openedFolderPath = null
                }
            )
        }
    }
}

@Composable
private fun FolderListScreen(
    folderMap: Map<String, List<Track>>,
    excludedFolders: Set<String>,
    onPickSafFolder: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onExcludeFolder: (String) -> Unit
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${folderMap.size} active directories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onPickSafFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Folder")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (folderMap.isEmpty() && excludedFolders.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No Accessible Music Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Add a directory via Android Storage Access Framework or rescan your device library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onPickSafFolder) {
                        Text("Authorize Directory")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folderMap.entries.toList(), key = { "folder_${it.key}" }) { (folderPath, tracks) ->
                    FolderCard(
                        folderPath = folderPath,
                        trackCount = tracks.size,
                        onClick = { onOpenFolder(folderPath) },
                        onPlay = { },  // Play handled in detail screen
                        onExclude = { onExcludeFolder(folderPath) }
                    )
                }

                if (excludedFolders.isNotEmpty()) {
                    item(key = "excluded_header") {
                        Text(
                            text = "Excluded Folders",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        items = excludedFolders.toList(),
                        key = { "excl_$it" }
                    ) { excludedPath ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = excludedPath,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(onClick = { onExcludeFolder(excludedPath) }) {
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    folderPath: String,
    trackCount: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onExclude: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderPath.substringAfterLast('/').ifBlank { folderPath },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$trackCount tracks · ${folderPath.substringBeforeLast('/').takeLast(40)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onExclude) {
                Icon(Icons.Default.VisibilityOff, contentDescription = "Exclude Folder",
                    tint = colors.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun FolderDetailScreen(
    folderPath: String,
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayFolder: () -> Unit,
    onPlayTrack: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onExcludeFolder: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val folderName = folderPath.substringAfterLast('/').ifBlank { folderPath }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Surface(
            color = colors.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surfaceContainerHigh
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = onPlayFolder,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play All")
                }
                IconButton(onClick = onExcludeFolder) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = "Exclude Folder",
                        tint = colors.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                ListItem(
                    headlineContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (track.isCueTrack) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                    color = colors.tertiaryContainer,
                                    contentColor = colors.onTertiaryContainer
                                ) {
                                    Text(
                                        text = "CUE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        val cueInfo = if (track.isCueTrack) " · CUE" else ""
                        Text(
                            text = "${track.artist} · ${track.sourceMetadata.toPrimaryTechnicalLine()}$cueInfo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = colors.secondaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = colors.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onPlayNext(track) }) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = "Play Next")
                        }
                    },
                    modifier = Modifier.clickable { onPlayTrack(idx) }
                )
                if (idx < tracks.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// ─── Playlists ───────────────────────────────────────────────────────────────

@Composable
fun PlaylistsScreen(viewModel: PlayerViewModel) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val allTracks by viewModel.sortedFilteredTracks.collectAsStateWithLifecycle()

    var newPlaylistName by remember { mutableStateOf("") }
    var activePlaylistForAdding by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                placeholder = { Text("Create new playlist...") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(10.dp))
            Button(
                enabled = newPlaylistName.isNotBlank(),
                onClick = {
                    viewModel.createPlaylist(newPlaylistName)
                    newPlaylistName = ""
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Create")
            }
        }

        if (playlists.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "No User Playlists Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Enter a name above to create a persistent local playlist. Playlists are preserved across library rescans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists, key = { it.playlistId }) { playlist ->
                    val isExpanded = expandedPlaylistId == playlist.playlistId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedPlaylistId = if (isExpanded) null else playlist.playlistId
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${playlist.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { activePlaylistForAdding = playlist },
                                        enabled = allTracks.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = "Add Track to Playlist")
                                    }
                                    IconButton(
                                        onClick = { viewModel.playTracks(playlist.tracks, 0) },
                                        enabled = playlist.tracks.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Playlist")
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(playlist.playlistId) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Playlist")
                                    }
                                }
                            }

                            if (isExpanded && playlist.tracks.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                playlist.tracks.forEachIndexed { idx, track ->
                                    ListItem(
                                        headlineContent = {
                                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        supportingContent = {
                                            Text(
                                                text = "${track.artist} · ${track.sourceMetadata.toPrimaryTechnicalLine()}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            viewModel.playTracks(playlist.tracks, idx)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activePlaylistForAdding?.let { targetPlaylist ->
        TrackPickerDialog(
            playlistName = targetPlaylist.name,
            availableTracks = allTracks,
            onSelectTrack = { track ->
                viewModel.addTrackToPlaylist(
                    playlistId = targetPlaylist.playlistId,
                    trackId = track.id,
                    nextIndex = targetPlaylist.tracks.size
                )
                activePlaylistForAdding = null
            },
            onDismiss = { activePlaylistForAdding = null }
        )
    }
}

@Composable
private fun TrackPickerDialog(
    playlistName: String,
    availableTracks: List<Track>,
    onSelectTrack: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Track to \"$playlistName\"") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(availableTracks, key = { it.id }) { track ->
                    ListItem(
                        headlineContent = {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.clickable { onSelectTrack(track) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
