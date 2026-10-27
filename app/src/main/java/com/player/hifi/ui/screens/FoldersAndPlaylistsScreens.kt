package com.player.hifi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun FoldersScreen(
    viewModel: PlayerViewModel,
    onPickSafFolder: () -> Unit
) {
    val folderMap by viewModel.folderMap.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var expandedFolderPath by remember { mutableStateOf<String?>(null) }

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

        if (folderMap.isEmpty() && settings.excludedFolders.isEmpty()) {
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
                folderMap.entries.forEach { (folderPath, tracksInFolder) ->
                    val isExpanded = expandedFolderPath == folderPath
                    item(key = "folder_$folderPath") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedFolderPath = if (isExpanded) null else folderPath
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = folderPath.substringAfterLast('/').ifBlank { folderPath },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$folderPath · ${tracksInFolder.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { viewModel.playTracks(tracksInFolder, 0) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Folder")
                                        }
                                        IconButton(onClick = { viewModel.toggleExcludeFolder(folderPath) }) {
                                            Icon(Icons.Default.VisibilityOff, contentDescription = "Exclude Folder")
                                        }
                                    }
                                }

                                if (isExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    tracksInFolder.forEachIndexed { idx, track ->
                                        ListItem(
                                            headlineContent = {
                                                Text(
                                                    text = track.fileName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            },
                                            supportingContent = {
                                                Text(
                                                    text = "${track.title} · ${track.sourceMetadata.toPrimaryTechnicalLine()}",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            },
                                            trailingContent = {
                                                IconButton(onClick = { viewModel.playNextInQueue(track) }) {
                                                    Icon(Icons.Default.PlaylistPlay, contentDescription = "Play Next")
                                                }
                                            },
                                            modifier = Modifier.clickable {
                                                viewModel.playTracks(tracksInFolder, idx)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (settings.excludedFolders.isNotEmpty()) {
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
                        items = settings.excludedFolders.toList(),
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
                                TextButton(onClick = { viewModel.toggleExcludeFolder(excludedPath) }) {
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