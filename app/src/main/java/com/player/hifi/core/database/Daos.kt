package com.player.hifi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksSnapshot(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): TrackEntity?

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE id NOT IN (:validIds)")
    suspend fun pruneRemovedTracks(validIds: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET lastPlayedEpochMillis = :timestampMs, playCount = playCount + 1 WHERE id = :trackId")
    suspend fun recordTrackPlayed(trackId: String, timestampMs: Long)

    @Query("UPDATE tracks SET artworkUriString = :artworkUri WHERE album = :album")
    suspend fun updateAlbumArtwork(album: String, artworkUri: String)

    @Query("UPDATE tracks SET artworkUriString = :artworkUri WHERE id = :trackId")
    suspend fun updateTrackArtwork(trackId: String, artworkUri: String)

    @Query("SELECT * FROM playlists ORDER BY createdAtEpochMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY positionIndex ASC")
    fun observePlaylistCrossRefs(): Flow<List<PlaylistTrackCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistEntity(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Transaction
    suspend fun deletePlaylist(playlistId: Long) {
        clearPlaylistTracks(playlistId)
        deletePlaylistEntity(playlistId)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND positionIndex = :positionIndex")
    suspend fun removeTrackFromPlaylistAt(playlistId: Long, positionIndex: Int)
}
