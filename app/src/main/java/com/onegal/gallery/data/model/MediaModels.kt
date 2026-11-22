package com.onegal.gallery.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class StorageSource {
    INTERNAL_MEDIASTORE,
    EXTERNAL_SAF
}

enum class GalleryTab(val label: String) {
    PICTURES("Pictures"),
    ALBUMS("Albums"),
    EXTERNAL("External"),
    MENU("Menu")
}

enum class SortOrder(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    SIZE_DESC("Largest first")
}

enum class ThemePreference(val label: String) {
    DARK_AMOLED("Pitch-Black AMOLED Dark"),
    LIGHT("One UI Light"),
    SYSTEM("System Default")
}

enum class SpecialCollection(val title: String) {
    FAVORITES("Favorites"),
    VIDEOS("Videos"),
    RECENT("Recent"),
    TRASH("Recycle bin")
}

enum class SmartMediaFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    SCREENSHOTS("Screenshots"),
    FAVORITES("Favorites")
}

@Immutable
data class MediaItem(
    val id: String,
    val mediaStoreId: Long? = null,
    val uri: Uri,
    val displayName: String,
    val dateTakenMs: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long = 0L,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val source: StorageSource,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val treeUri: Uri? = null,
    val documentId: String? = null
) {
    val type: MediaType
        get() = if (mimeType.startsWith("video/", ignoreCase = true)) MediaType.VIDEO else MediaType.IMAGE

    val isVideo: Boolean
        get() = type == MediaType.VIDEO

    val isScreenshot: Boolean
        get() {
            val bucket = bucketName.lowercase()
            val path = relativePath.lowercase()
            val name = displayName.lowercase()
            return bucket.contains("screenshot") ||
                path.contains("screenshot") ||
                name.startsWith("screenshot") ||
                name.contains("_screenshot")
        }

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) (width.toFloat() / height.toFloat()).coerceIn(0.62f, 1.85f) else 1f

    val formatBadge: String?
        get() {
            val lowerName = displayName.lowercase()
            return when {
                lowerName.endsWith(".dng") || lowerName.endsWith(".raw") || mimeType.contains("dng") -> "RAW"
                lowerName.endsWith(".gif") || mimeType.equals("image/gif", ignoreCase = true) -> "GIF"
                isVideo && (width >= 7000 || height >= 4000) -> "8K"
                isVideo && (width >= 3800 || height >= 2100) -> "4K"
                else -> null
            }
        }
}

@Immutable
data class CustomAlbumRecord(
    val id: String,
    val name: String,
    val source: StorageSource,
    val relativePath: String,
    val treeUriString: String? = null,
    val documentId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Immutable
data class AlbumItem(
    val id: String,
    val name: String,
    val coverItem: MediaItem?,
    val itemCount: Int,
    val totalSizeBytes: Long,
    val source: StorageSource,
    val relativePath: String,
    val treeUri: Uri? = null,
    val documentId: String? = null,
    val isMerged: Boolean = false,
    val mergedBucketIds: Set<String> = setOf(id),
    val isCustomEmpty: Boolean = false
)

@Immutable
data class ExternalDriveInfo(
    val treeUri: Uri,
    val label: String,
    val rootDocumentId: String,
    val folders: List<AlbumItem>,
    val items: List<MediaItem>,
    val totalBytes: Long,
    val scanTimeMs: Long
)

@Immutable
data class ExifDetails(
    val cameraMake: String?,
    val cameraModel: String?,
    val aperture: String?,
    val exposureTime: String?,
    val iso: String?,
    val focalLength: String?,
    val lensModel: String?,
    val dateTimeOriginal: String?,
    val gpsCoordinates: String?,
    val width: Int,
    val height: Int
)

@Immutable
data class SmartMediaScanResult(
    val detectedText: String? = null,
    val qrUrl: String? = null,
    val qrRawValue: String? = null
) {
    val hasActionableContent: Boolean
        get() = !qrUrl.isNullOrBlank() || !qrRawValue.isNullOrBlank() || !detectedText.isNullOrBlank()
}

@Immutable
data class DateSection(
    val key: String,
    val title: String,
    val monthBadge: String,
    val items: List<MediaItem>
)

@Immutable
data class GallerySettings(
    val fullScreenScrolling: Boolean = true,
    val autoPlayVideos: Boolean = true,
    val originalAspectRatioGrid: Boolean = false,
    val showFormatBadges: Boolean = true,
    val essentialAlbumsEnabled: Boolean = false,
    val essentialAlbumIds: Set<String> = emptySet(),
    val mergeAlbums: Boolean = false,
    val hiddenAlbumIds: Set<String> = emptySet(),
    val showEmptyAlbums: Boolean = true,
    val showFullExifLocation: Boolean = true,
    val trashRetention30Days: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.DARK_AMOLED
)
