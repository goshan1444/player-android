package com.player.hifi.core.metadata

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object OnlineArtworkFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    suspend fun fetchAlbumArtwork(
        context: Context,
        artist: String,
        album: String,
        trackTitle: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val cleanArtist = artist.trim().takeIf {
            it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true)
        } ?: ""

        val isGenericAlbum = album.isBlank() ||
                album.equals("Unknown Album", ignoreCase = true) ||
                album.equals("AyuGram", ignoreCase = true) ||
                album.equals("Telegram", ignoreCase = true) ||
                album.equals("Telegram Audio", ignoreCase = true) ||
                album.equals("Music", ignoreCase = true) ||
                album.equals("Download", ignoreCase = true)

        val cleanAlbum = if (!isGenericAlbum) album.trim() else ""
        val cleanTitle = trackTitle?.trim()?.takeIf { it.isNotBlank() } ?: ""

        if (cleanArtist.isBlank() && cleanAlbum.isBlank() && cleanTitle.isBlank()) {
            return@withContext null
        }

        val cacheDir = File(context.filesDir, "album_art")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val safeHash = "${cleanArtist.lowercase()}_${cleanAlbum.lowercase()}_${cleanTitle.lowercase()}".hashCode()
            .toUInt().toString(16)
        val localCoverFile = File(cacheDir, "art_$safeHash.jpg")

        // Return cached cover if already downloaded and valid
        if (localCoverFile.exists() && localCoverFile.length() > 1024) {
            return@withContext Uri.fromFile(localCoverFile).toString()
        }

        // 1. Try iTunes Search API with album
        if (cleanAlbum.isNotBlank()) {
            val itunesAlbumUrl = queryItunesArtworkUrl(cleanArtist, cleanAlbum, isAlbumSearch = true)
            if (itunesAlbumUrl != null && downloadImageToFile(itunesAlbumUrl, localCoverFile)) {
                return@withContext Uri.fromFile(localCoverFile).toString()
            }
        }

        // 2. Try iTunes Search API with track title
        if (cleanTitle.isNotBlank()) {
            val itunesTitleUrl = queryItunesArtworkUrl(cleanArtist, cleanTitle, isAlbumSearch = false)
            if (itunesTitleUrl != null && downloadImageToFile(itunesTitleUrl, localCoverFile)) {
                return@withContext Uri.fromFile(localCoverFile).toString()
            }
        }

        // 3. Fallback to Deezer API with album
        if (cleanAlbum.isNotBlank()) {
            val deezerAlbumUrl = queryDeezerArtworkUrl(cleanArtist, cleanAlbum)
            if (deezerAlbumUrl != null && downloadImageToFile(deezerAlbumUrl, localCoverFile)) {
                return@withContext Uri.fromFile(localCoverFile).toString()
            }
        }

        // 4. Fallback to Deezer API with track title
        if (cleanTitle.isNotBlank()) {
            val deezerTitleUrl = queryDeezerArtworkUrl(cleanArtist, cleanTitle)
            if (deezerTitleUrl != null && downloadImageToFile(deezerTitleUrl, localCoverFile)) {
                return@withContext Uri.fromFile(localCoverFile).toString()
            }
        }

        null
    }

    private fun queryItunesArtworkUrl(artist: String, queryTerm: String, isAlbumSearch: Boolean): String? {
        val query = if (artist.isNotBlank()) "$artist $queryTerm" else queryTerm
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return null
        val entityParam = if (isAlbumSearch) "&entity=album" else ""
        val urlString = "https://itunes.apple.com/search?term=$encoded$entityParam&limit=1"

        return runCatching {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val results = json.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val firstItem = results.getJSONObject(0)
                val rawArtwork = firstItem.optString("artworkUrl100")
                if (rawArtwork.isNotBlank()) {
                    // Request high-res 600x600 instead of 100x100 thumbnail
                    rawArtwork.replace("100x100bb.jpg", "600x600bb.jpg")
                } else null
            } else null
        }.getOrNull()
    }

    private fun queryDeezerArtworkUrl(artist: String, queryTerm: String): String? {
        val query = if (artist.isNotBlank()) "$artist $queryTerm" else queryTerm
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return null
        val urlString = "https://api.deezer.com/search/album?q=$encoded&limit=1"

        return runCatching {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(response)
            val data = json.optJSONArray("data")
            if (data != null && data.length() > 0) {
                val item = data.getJSONObject(0)
                item.optString("cover_xl").takeIf { it.isNotBlank() }
                    ?: item.optString("cover_big").takeIf { it.isNotBlank() }
                    ?: item.optString("cover_medium").takeIf { it.isNotBlank() }
            } else null
        }.getOrNull()
    }

    private fun downloadImageToFile(imageUrl: String, targetFile: File): Boolean {
        return runCatching {
            val conn = URL(imageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return false
            }

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            targetFile.exists() && targetFile.length() > 1024
        }.getOrDefault(false)
    }
}
