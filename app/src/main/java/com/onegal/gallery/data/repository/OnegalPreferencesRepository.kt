package com.onegal.gallery.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import coil3.SingletonImageLoader
import com.onegal.gallery.data.model.CustomAlbumRecord
import com.onegal.gallery.data.model.ExternalDriveInfo
import com.onegal.gallery.data.model.GallerySettings
import com.onegal.gallery.data.model.StorageSource
import com.onegal.gallery.data.model.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.onegalDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onegal_local_prefs_v2"
)

/**
 * 100% local Jetpack DataStore persistence for Samsung One UI 8.0 Gallery settings
 * and the persistent Empty Album Registry (keeping 0-item user-created folders visible
 * even though Android MediaStore hides empty directories).
 */
class OnegalPreferencesRepository(private val context: Context) {

    private object Keys {
        val FULL_SCREEN_SCROLLING = booleanPreferencesKey("full_screen_scrolling")
        val AUTO_PLAY_VIDEOS = booleanPreferencesKey("auto_play_videos")
        val ORIGINAL_ASPECT_GRID = booleanPreferencesKey("original_aspect_grid")
        val SHOW_FORMAT_BADGES = booleanPreferencesKey("show_format_badges")
        val ESSENTIAL_ALBUMS_ENABLED = booleanPreferencesKey("essential_albums_enabled")
        val ESSENTIAL_ALBUM_IDS = stringSetPreferencesKey("essential_album_ids")
        val MERGE_ALBUMS = booleanPreferencesKey("merge_albums")
        val HIDDEN_ALBUM_IDS = stringSetPreferencesKey("hidden_album_ids")
        val SHOW_EMPTY_ALBUMS = booleanPreferencesKey("show_empty_albums")
        val SHOW_FULL_EXIF_LOCATION = booleanPreferencesKey("show_full_exif_location")
        val TRASH_RETENTION_30D = booleanPreferencesKey("trash_retention_30d")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val CUSTOM_ALBUMS_JSON = stringPreferencesKey("custom_albums_json")
    }

    val settingsFlow: Flow<GallerySettings> = context.onegalDataStore.data.map { prefs ->
        GallerySettings(
            fullScreenScrolling = prefs[Keys.FULL_SCREEN_SCROLLING] ?: true,
            autoPlayVideos = prefs[Keys.AUTO_PLAY_VIDEOS] ?: true,
            originalAspectRatioGrid = prefs[Keys.ORIGINAL_ASPECT_GRID] ?: false,
            showFormatBadges = prefs[Keys.SHOW_FORMAT_BADGES] ?: true,
            essentialAlbumsEnabled = prefs[Keys.ESSENTIAL_ALBUMS_ENABLED] ?: false,
            essentialAlbumIds = prefs[Keys.ESSENTIAL_ALBUM_IDS] ?: emptySet(),
            mergeAlbums = prefs[Keys.MERGE_ALBUMS] ?: false,
            hiddenAlbumIds = prefs[Keys.HIDDEN_ALBUM_IDS] ?: emptySet(),
            showEmptyAlbums = prefs[Keys.SHOW_EMPTY_ALBUMS] ?: true,
            showFullExifLocation = prefs[Keys.SHOW_FULL_EXIF_LOCATION] ?: true,
            trashRetention30Days = prefs[Keys.TRASH_RETENTION_30D] ?: true,
            themePreference = prefs[Keys.THEME_PREFERENCE]
                ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.DARK_AMOLED
        )
    }

    val customAlbumsFlow: Flow<List<CustomAlbumRecord>> = context.onegalDataStore.data.map { prefs ->
        decodeCustomAlbums(prefs[Keys.CUSTOM_ALBUMS_JSON].orEmpty())
    }

    suspend fun updateSettings(transform: (GallerySettings) -> GallerySettings) {
        context.onegalDataStore.edit { prefs ->
            val current = GallerySettings(
                fullScreenScrolling = prefs[Keys.FULL_SCREEN_SCROLLING] ?: true,
                autoPlayVideos = prefs[Keys.AUTO_PLAY_VIDEOS] ?: true,
                originalAspectRatioGrid = prefs[Keys.ORIGINAL_ASPECT_GRID] ?: false,
                showFormatBadges = prefs[Keys.SHOW_FORMAT_BADGES] ?: true,
                essentialAlbumsEnabled = prefs[Keys.ESSENTIAL_ALBUMS_ENABLED] ?: false,
                essentialAlbumIds = prefs[Keys.ESSENTIAL_ALBUM_IDS] ?: emptySet(),
                mergeAlbums = prefs[Keys.MERGE_ALBUMS] ?: false,
                hiddenAlbumIds = prefs[Keys.HIDDEN_ALBUM_IDS] ?: emptySet(),
                showEmptyAlbums = prefs[Keys.SHOW_EMPTY_ALBUMS] ?: true,
                showFullExifLocation = prefs[Keys.SHOW_FULL_EXIF_LOCATION] ?: true,
                trashRetention30Days = prefs[Keys.TRASH_RETENTION_30D] ?: true,
                themePreference = prefs[Keys.THEME_PREFERENCE]
                    ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                    ?: ThemePreference.DARK_AMOLED
            )
            val updated = transform(current)
            prefs[Keys.FULL_SCREEN_SCROLLING] = updated.fullScreenScrolling
            prefs[Keys.AUTO_PLAY_VIDEOS] = updated.autoPlayVideos
            prefs[Keys.ORIGINAL_ASPECT_GRID] = updated.originalAspectRatioGrid
            prefs[Keys.SHOW_FORMAT_BADGES] = updated.showFormatBadges
            prefs[Keys.ESSENTIAL_ALBUMS_ENABLED] = updated.essentialAlbumsEnabled
            prefs[Keys.ESSENTIAL_ALBUM_IDS] = updated.essentialAlbumIds
            prefs[Keys.MERGE_ALBUMS] = updated.mergeAlbums
            prefs[Keys.HIDDEN_ALBUM_IDS] = updated.hiddenAlbumIds
            prefs[Keys.SHOW_EMPTY_ALBUMS] = updated.showEmptyAlbums
            prefs[Keys.SHOW_FULL_EXIF_LOCATION] = updated.showFullExifLocation
            prefs[Keys.TRASH_RETENTION_30D] = updated.trashRetention30Days
            prefs[Keys.THEME_PREFERENCE] = updated.themePreference.name
        }
    }

    /**
     * Physically creates the folder (Internal Pictures/<Name> via File.mkdirs() or
     * External USB-C/NVMe via DocumentFile.createDirectory()) without any .nomedia file,
     * and persists the record in DataStore so 0-item albums remain immediately visible.
     */
    suspend fun createPersistentAlbum(
        rawName: String,
        targetExternalDrive: ExternalDriveInfo?
    ): CustomAlbumRecord? = withContext(Dispatchers.IO) {
        val sanitized = rawName
            .trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .take(64)
        if (sanitized.isBlank()) return@withContext null

        val record = if (targetExternalDrive != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, targetExternalDrive.treeUri)
                ?: return@withContext null
            val existingOrCreated = rootDoc.findFile(sanitized)
                ?: rootDoc.createDirectory(sanitized)
                ?: return@withContext null
            val createdDocId = runCatching {
                DocumentsContract.getDocumentId(existingOrCreated.uri)
            }.getOrNull() ?: "${targetExternalDrive.rootDocumentId}/$sanitized"

            CustomAlbumRecord(
                id = createdDocId,
                name = sanitized,
                source = StorageSource.EXTERNAL_SAF,
                relativePath = "${targetExternalDrive.label}/$sanitized/",
                treeUriString = targetExternalDrive.treeUri.toString(),
                documentId = createdDocId
            )
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetDir = File(picturesDir, sanitized)
            runCatching {
                if (!targetDir.exists()) targetDir.mkdirs()
                // Ensure no .nomedia file suppresses MediaStore once photos are moved in
                val nomedia = File(targetDir, ".nomedia")
                if (nomedia.exists()) nomedia.delete()
            }
            val relPath = "${Environment.DIRECTORY_PICTURES}/$sanitized/"
            CustomAlbumRecord(
                id = "custom_internal_${sanitized.lowercase()}_${UUID.randomUUID().toString().take(6)}",
                name = sanitized,
                source = StorageSource.INTERNAL_MEDIASTORE,
                relativePath = relPath
            )
        }

        context.onegalDataStore.edit { prefs ->
            val existing = decodeCustomAlbums(prefs[Keys.CUSTOM_ALBUMS_JSON].orEmpty())
            val deduped = existing.filterNot {
                it.name.equals(record.name, ignoreCase = true) && it.source == record.source
            }
            prefs[Keys.CUSTOM_ALBUMS_JSON] = encodeCustomAlbums(listOf(record) + deduped)
        }

        record
    }

    suspend fun removeCustomAlbum(albumId: String) {
        context.onegalDataStore.edit { prefs ->
            val existing = decodeCustomAlbums(prefs[Keys.CUSTOM_ALBUMS_JSON].orEmpty())
            prefs[Keys.CUSTOM_ALBUMS_JSON] = encodeCustomAlbums(existing.filterNot { it.id == albumId })
        }
    }

    suspend fun computeThumbnailCacheBytes(): Long = withContext(Dispatchers.IO) {
        val dir = context.cacheDir.resolve("onegal_thumb_cache")
        if (!dir.exists()) return@withContext 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearThumbnailCache(): Long = withContext(Dispatchers.IO) {
        val loader = SingletonImageLoader.get(context)
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
        val dir = context.cacheDir.resolve("onegal_thumb_cache")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        0L
    }

    private fun encodeCustomAlbums(records: List<CustomAlbumRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("source", r.source.name)
                    put("relativePath", r.relativePath)
                    put("treeUriString", r.treeUriString ?: JSONObject.NULL)
                    put("documentId", r.documentId ?: JSONObject.NULL)
                    put("createdAtMs", r.createdAtMs)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeCustomAlbums(raw: String): List<CustomAlbumRecord> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                CustomAlbumRecord(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    source = StorageSource.valueOf(obj.getString("source")),
                    relativePath = obj.getString("relativePath"),
                    treeUriString = obj.optString("treeUriString").takeIf { it.isNotBlank() && it != "null" },
                    documentId = obj.optString("documentId").takeIf { it.isNotBlank() && it != "null" },
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }
}
