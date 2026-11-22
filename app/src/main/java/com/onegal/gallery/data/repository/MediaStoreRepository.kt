package com.onegal.gallery.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.onegal.gallery.data.model.AlbumItem
import com.onegal.gallery.data.model.ExifDetails
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.data.model.StorageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        resolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        trySend(Unit)
        awaitClose {
            resolver.unregisterContentObserver(observer)
        }
    }

    suspend fun loadInternalMedia(includeTrashedOnly: Boolean = false): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val items = ArrayList<MediaItem>(512)
            val collection = MediaStore.Files.getContentUri("external")

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Video.VideoColumns.DURATION,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.IS_FAVORITE,
                MediaStore.MediaColumns.IS_TRASHED
            )

            val queryArgs = Bundle().apply {
                val baseSelection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND ${MediaStore.Files.FileColumns.SIZE} > 0"
                if (includeTrashedOnly) {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "$baseSelection AND ${MediaStore.MediaColumns.IS_TRASHED} = 1"
                    )
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                } else {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SELECTION,
                        "$baseSelection AND ${MediaStore.MediaColumns.IS_TRASHED} = 0"
                    )
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                }
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    arrayOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                    )
                )
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "COALESCE(${MediaStore.Files.FileColumns.DATE_TAKEN}, ${MediaStore.Files.FileColumns.DATE_MODIFIED} * 1000) DESC"
                )
            }

            resolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val relPathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val favCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
                val trashCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_TRASHED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(typeCol)
                    val contentUri = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaId)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId)
                    }
                    val dateModifiedSec = cursor.getLong(modCol)
                    val rawDateTaken = cursor.getLong(takenCol)
                    val dateTakenMs = if (rawDateTaken > 0L) rawDateTaken else dateModifiedSec * 1000L
                    val bucketName = cursor.getString(bucketNameCol)?.ifBlank { "Internal Storage" } ?: "Internal Storage"
                    val bucketId = cursor.getString(bucketIdCol) ?: bucketName
                    val relPath = cursor.getString(relPathCol) ?: "DCIM/"
                    val mime = cursor.getString(mimeCol) ?: if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) "video/mp4" else "image/jpeg"

                    items.add(
                        MediaItem(
                            id = "ms_$mediaId",
                            mediaStoreId = mediaId,
                            uri = contentUri,
                            displayName = cursor.getString(nameCol) ?: "IMG_$mediaId",
                            dateTakenMs = dateTakenMs,
                            dateModifiedSec = dateModifiedSec,
                            sizeBytes = cursor.getLong(sizeCol),
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol),
                            durationMs = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) cursor.getLong(durCol) else 0L,
                            mimeType = mime,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            relativePath = relPath,
                            source = StorageSource.INTERNAL_MEDIASTORE,
                            isFavorite = cursor.getInt(favCol) == 1,
                            isTrashed = cursor.getInt(trashCol) == 1
                        )
                    )
                }
            }
            items
        }

    fun buildAlbumsFromMedia(items: List<MediaItem>): List<AlbumItem> {
        return items
            .groupBy { it.bucketId }
            .mapNotNull { (bucketId, bucketItems) ->
                val sorted = bucketItems.sortedByDescending { it.dateTakenMs }
                val cover = sorted.firstOrNull() ?: return@mapNotNull null
                AlbumItem(
                    id = bucketId,
                    name = cover.bucketName,
                    coverItem = cover,
                    itemCount = sorted.size,
                    totalSizeBytes = sorted.sumOf { it.sizeBytes },
                    source = cover.source,
                    relativePath = cover.relativePath,
                    treeUri = cover.treeUri,
                    documentId = cover.documentId
                )
            }
            .sortedWith(
                compareByDescending<AlbumItem> { it.name.equals("Camera", ignoreCase = true) }
                    .thenByDescending { it.coverItem?.dateTakenMs ?: 0L }
            )
    }

    suspend fun readExifDetails(item: MediaItem): ExifDetails = withContext(Dispatchers.IO) {
        if (item.isVideo) {
            return@withContext ExifDetails(
                cameraMake = null,
                cameraModel = null,
                aperture = null,
                exposureTime = null,
                iso = null,
                focalLength = null,
                lensModel = null,
                dateTimeOriginal = null,
                gpsCoordinates = null,
                width = item.width,
                height = item.height
            )
        }
        try {
            val targetUri = if (item.source == StorageSource.INTERNAL_MEDIASTORE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.setRequireOriginal(item.uri) }.getOrDefault(item.uri)
            } else {
                item.uri
            }
            resolver.openInputStream(targetUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                val gps = latLong?.let {
                    String.format(Locale.US, "%.5f, %.5f", it[0], it[1])
                }
                val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
                val rawShutter = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val exposure = when {
                    rawShutter <= 0.0 -> null
                    rawShutter >= 1.0 -> String.format(Locale.US, "%.1fs", rawShutter)
                    else -> "1/${(1.0 / rawShutter).toInt()}s"
                }
                val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" }
                val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    .takeIf { it > 0.0 }
                    ?.let { String.format(Locale.US, "%.1f mm", it) }

                ExifDetails(
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    aperture = fNumber,
                    exposureTime = exposure,
                    iso = iso,
                    focalLength = focal,
                    lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                    dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    gpsCoordinates = gps,
                    width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, item.width).takeIf { it > 0 } ?: item.width,
                    height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, item.height).takeIf { it > 0 } ?: item.height
                )
            } ?: fallbackExif(item)
        } catch (_: Exception) {
            fallbackExif(item)
        }
    }

    private fun fallbackExif(item: MediaItem) = ExifDetails(
        cameraMake = null,
        cameraModel = null,
        aperture = null,
        exposureTime = null,
        iso = null,
        focalLength = null,
        lensModel = null,
        dateTimeOriginal = null,
        gpsCoordinates = null,
        width = item.width,
        height = item.height
    )

    fun createFavoriteIntentSender(uris: List<Uri>, favorite: Boolean): IntentSender {
        return MediaStore.createFavoriteRequest(resolver, uris, favorite).intentSender
    }

    fun createTrashIntentSender(uris: List<Uri>, trash: Boolean): IntentSender {
        return MediaStore.createTrashRequest(resolver, uris, trash).intentSender
    }

    fun createDeleteIntentSender(uris: List<Uri>): IntentSender {
        return MediaStore.createDeleteRequest(resolver, uris).intentSender
    }

    fun createWriteIntentSender(uris: List<Uri>): IntentSender {
        return MediaStore.createWriteRequest(resolver, uris).intentSender
    }

    suspend fun renameInternalMedia(item: MediaItem, newDisplayName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName)
                }
                resolver.update(item.uri, values, null, null) > 0
            } catch (_: SecurityException) {
                false
            }
        }

    suspend fun copyMediaToInternalAlbum(
        sourceItem: MediaItem,
        targetRelativePath: String
    ): Uri? = withContext(Dispatchers.IO) {
        val normalizedPath = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
        val collection = if (sourceItem.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceItem.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, sourceItem.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, normalizedPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destUri = resolver.insert(collection, values) ?: return@withContext null
        try {
            resolver.openInputStream(sourceItem.uri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                } ?: throw IOException("Failed to open destination stream")
            } ?: throw IOException("Failed to open source stream")

            val commitValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(destUri, commitValues, null, null)
            destUri
        } catch (_: Exception) {
            resolver.delete(destUri, null, null)
            null
        }
    }

    suspend fun copyMediaToExternalFolder(
        sourceItem: MediaItem,
        targetFolderTreeUri: Uri,
        targetFolderDocId: String
    ): Uri? = withContext(Dispatchers.IO) {
        val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            targetFolderTreeUri,
            targetFolderDocId
        )
        val parentFolder = DocumentFile.fromTreeUri(context, parentDocUri)
            ?: DocumentFile.fromSingleUri(context, parentDocUri)
            ?: return@withContext null
        val created = parentFolder.createFile(sourceItem.mimeType, sourceItem.displayName)
            ?: return@withContext null
        try {
            resolver.openInputStream(sourceItem.uri)?.use { input ->
                resolver.openOutputStream(created.uri)?.use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                } ?: throw IOException("Failed to open external output stream")
            } ?: throw IOException("Failed to open source input stream")
            created.uri
        } catch (_: Exception) {
            created.delete()
            null
        }
    }

    suspend fun saveVideoFramePng(bitmap: Bitmap, sourceVideoName: String): Uri? =
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = sourceVideoName.substringBeforeLast('.').take(32)
            val fileName = "${baseName}_frame_${timestamp}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Onegal Captures"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: return@withContext null

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, finalizeValues, null, null)
                uri
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }
}
