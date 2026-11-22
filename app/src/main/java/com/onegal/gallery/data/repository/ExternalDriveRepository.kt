package com.onegal.gallery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.onegal.gallery.data.model.AlbumItem
import com.onegal.gallery.data.model.ExternalDriveInfo
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.data.model.StorageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

/**
 * Direct Storage Access Framework (SAF) external USB-C / NVMe drive indexer.
 * Queries DocumentsContract.buildChildDocumentsUriUsingTree directly on Dispatchers.IO
 * so newly plugged-in external SSDs appear instantaneously without waiting for Android MediaStore.
 */
class ExternalDriveRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    private val supportedImageExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "dng", "raw", "gif", "avif")
    private val supportedVideoExt = setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "3gp", "ts")

    fun persistTreePermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            resolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    fun releaseTreePermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            resolver.releasePersistableUriPermission(treeUri, flags)
        }
    }

    fun getPersistedExternalTreeUris(): List<Uri> {
        return resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }
            .distinct()
    }

    suspend fun scanAllMountedDrives(): List<ExternalDriveInfo> = withContext(Dispatchers.IO) {
        val uris = getPersistedExternalTreeUris()
        uris.mapNotNull { treeUri ->
            scanDriveTree(treeUri)
        }
    }

    suspend fun scanDriveTree(treeUri: Uri): ExternalDriveInfo? = withContext(Dispatchers.IO) {
        val startMs = SystemClock.elapsedRealtime()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext null

        // Verify the external drive is currently mounted and readable
        val rootProbeUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
        val rootLabel = querySingleDisplayName(rootProbeUri) ?: extractDriveLabel(rootDocId)
        val canProbeChildren = runCatching {
            val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocId)
            resolver.query(childUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use {
                true
            } ?: false
        }.getOrDefault(false)

        if (!canProbeChildren) {
            return@withContext null
        }

        val allMedia = ArrayList<MediaItem>(256)
        val folderMediaMap = LinkedHashMap<String, MutableList<MediaItem>>()
        val folderNameMap = HashMap<String, String>()
        val folderPathMap = HashMap<String, String>()

        data class DirNode(val docId: String, val folderName: String, val relativePath: String)

        val queue = ArrayDeque<DirNode>()
        queue.add(DirNode(rootDocId, rootLabel, "$rootLabel/"))

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current.docId)

            runCatching {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idIdx) ?: continue
                        val displayName = cursor.getString(nameIdx) ?: continue
                        val mimeType = cursor.getString(mimeIdx) ?: ""

                        if (displayName.startsWith(".") || displayName.equals("Android", ignoreCase = true) ||
                            displayName.equals("System Volume Information", ignoreCase = true) ||
                            displayName.equals("\$RECYCLE.BIN", ignoreCase = true)
                        ) {
                            continue
                        }

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(
                                DirNode(
                                    docId = docId,
                                    folderName = displayName,
                                    relativePath = "${current.relativePath}$displayName/"
                                )
                            )
                        } else {
                            val resolvedMime = resolveMediaMime(displayName, mimeType) ?: continue
                            val lastModMs = cursor.getLong(modIdx).takeIf { it > 0L } ?: System.currentTimeMillis()
                            val sizeBytes = cursor.getLong(sizeIdx)
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

                            val item = MediaItem(
                                id = "saf_${treeUri.hashCode()}_${docId.hashCode()}",
                                mediaStoreId = null,
                                uri = docUri,
                                displayName = displayName,
                                dateTakenMs = lastModMs,
                                dateModifiedSec = lastModMs / 1000L,
                                sizeBytes = sizeBytes,
                                width = 0,
                                height = 0,
                                durationMs = 0L,
                                mimeType = resolvedMime,
                                bucketId = current.docId,
                                bucketName = current.folderName,
                                relativePath = current.relativePath,
                                source = StorageSource.EXTERNAL_SAF,
                                isFavorite = false,
                                isTrashed = false,
                                treeUri = treeUri,
                                documentId = docId
                            )
                            allMedia.add(item)
                            folderMediaMap.getOrPut(current.docId) { ArrayList() }.add(item)
                            folderNameMap[current.docId] = current.folderName
                            folderPathMap[current.docId] = current.relativePath
                        }
                    }
                }
            }
        }

        val sortedMedia = allMedia.sortedByDescending { it.dateTakenMs }
        val folders = folderMediaMap.mapNotNull { (folderDocId, folderItems) ->
            val sortedFolder = folderItems.sortedByDescending { it.dateTakenMs }
            val cover = sortedFolder.firstOrNull() ?: return@mapNotNull null
            AlbumItem(
                id = folderDocId,
                name = folderNameMap[folderDocId] ?: rootLabel,
                coverItem = cover,
                itemCount = sortedFolder.size,
                totalSizeBytes = sortedFolder.sumOf { it.sizeBytes },
                source = StorageSource.EXTERNAL_SAF,
                relativePath = folderPathMap[folderDocId] ?: "$rootLabel/",
                treeUri = treeUri,
                documentId = folderDocId
            )
        }.sortedByDescending { it.itemCount }

        val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)

        ExternalDriveInfo(
            treeUri = treeUri,
            label = rootLabel,
            rootDocumentId = rootDocId,
            folders = folders,
            items = sortedMedia,
            totalBytes = sortedMedia.sumOf { it.sizeBytes },
            scanTimeMs = elapsed
        )
    }

    suspend fun renameExternalDocument(item: MediaItem, newDisplayName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                DocumentsContract.renameDocument(resolver, item.uri, newDisplayName) != null
            }.getOrDefault(false)
        }

    suspend fun deleteExternalDocument(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            DocumentsContract.deleteDocument(resolver, item.uri)
        }.getOrElse {
            DocumentFile.fromSingleUri(context, item.uri)?.delete() ?: false
        }
    }

    private fun resolveMediaMime(displayName: String, rawMime: String): String? {
        if (rawMime.startsWith("image/", ignoreCase = true) || rawMime.startsWith("video/", ignoreCase = true)) {
            return rawMime
        }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when {
            ext in supportedImageExt -> "image/${if (ext == "jpg") "jpeg" else ext}"
            ext in supportedVideoExt -> "video/$ext"
            else -> null
        }
    }

    private fun querySingleDisplayName(docUri: Uri): String? {
        return runCatching {
            resolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()
    }

    private fun extractDriveLabel(rootDocId: String): String {
        val volumePart = rootDocId.substringBefore(':').ifBlank { "USB-C Drive" }
        return if (volumePart.equals("primary", ignoreCase = true)) "Internal Storage" else "USB Drive ($volumePart)"
    }
}
