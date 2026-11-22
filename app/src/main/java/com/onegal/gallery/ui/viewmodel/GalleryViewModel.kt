package com.onegal.gallery.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onegal.gallery.data.ml.SmartMediaAnalyzer
import com.onegal.gallery.data.model.AlbumItem
import com.onegal.gallery.data.model.CustomAlbumRecord
import com.onegal.gallery.data.model.DateSection
import com.onegal.gallery.data.model.ExifDetails
import com.onegal.gallery.data.model.ExternalDriveInfo
import com.onegal.gallery.data.model.GallerySettings
import com.onegal.gallery.data.model.GalleryTab
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.data.model.SmartMediaFilter
import com.onegal.gallery.data.model.SmartMediaScanResult
import com.onegal.gallery.data.model.SortOrder
import com.onegal.gallery.data.model.SpecialCollection
import com.onegal.gallery.data.model.StorageSource
import com.onegal.gallery.data.repository.ExternalDriveRepository
import com.onegal.gallery.data.repository.MediaStoreRepository
import com.onegal.gallery.data.repository.OnegalPreferencesRepository
import com.onegal.gallery.system.UsbDriveEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class GalleryUiState(
    val hasMediaPermission: Boolean = false,
    val isLoading: Boolean = false,
    val activeTab: GalleryTab = GalleryTab.PICTURES,
    val settings: GallerySettings = GallerySettings(),
    val customAlbums: List<CustomAlbumRecord> = emptyList(),
    val thumbnailCacheBytes: Long = 0L,
    val picturesColumns: Int = 4,
    val albumsColumns: Int = 3,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val activeMediaFilter: SmartMediaFilter = SmartMediaFilter.ALL,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val internalMedia: List<MediaItem> = emptyList(),
    val trashedMedia: List<MediaItem> = emptyList(),
    val internalAlbums: List<AlbumItem> = emptyList(),
    val externalDrives: List<ExternalDriveInfo> = emptyList(),
    val pendingUsbPromptDevice: String? = null,
    val openedAlbum: AlbumItem? = null,
    val openedCollection: SpecialCollection? = null,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isMenuSheetOpen: Boolean = false,
    val isSettingsScreenOpen: Boolean = false,
    val viewerItems: List<MediaItem> = emptyList(),
    val viewerIndex: Int? = null,
    val exifCache: Map<String, ExifDetails> = emptyMap(),
    val smartScanCache: Map<String, SmartMediaScanResult> = emptyMap(),
    val activeCutoutBitmap: Bitmap? = null,
    val activeCutoutItemId: String? = null,
    val isExtractingCutout: Boolean = false,
    val toastMessage: String? = null
) {
    val currentExif: ExifDetails?
        get() = viewerIndex?.let { idx -> viewerItems.getOrNull(idx)?.id }?.let { exifCache[it] }

    val currentSmartScan: SmartMediaScanResult?
        get() = viewerIndex?.let { idx -> viewerItems.getOrNull(idx)?.id }?.let { smartScanCache[it] }
    /**
     * All albums including persistent 0-item user-created albums and optional Internal+External name merging.
     */
    val allAvailableAlbums: List<AlbumItem>
        get() {
            val baseList = ArrayList<AlbumItem>(internalAlbums)
            val externalFolders = externalDrives.flatMap { it.folders }

            // Inject persistent 0-item empty albums when enabled
            if (settings.showEmptyAlbums) {
                for (record in customAlbums) {
                    val alreadyHasMedia = (baseList + externalFolders).any {
                        it.name.equals(record.name, ignoreCase = true) && it.source == record.source
                    }
                    if (!alreadyHasMedia) {
                        val isExternalRecord = record.source == StorageSource.EXTERNAL_SAF
                        val driveConnected = !isExternalRecord || externalDrives.any {
                            it.treeUri.toString() == record.treeUriString
                        }
                        if (driveConnected) {
                            baseList.add(
                                AlbumItem(
                                    id = record.id,
                                    name = record.name,
                                    coverItem = null,
                                    itemCount = 0,
                                    totalSizeBytes = 0L,
                                    source = record.source,
                                    relativePath = record.relativePath,
                                    treeUri = record.treeUriString?.let { Uri.parse(it) },
                                    documentId = record.documentId,
                                    isCustomEmpty = true
                                )
                            )
                        }
                    }
                }
            }

            if (!settings.mergeAlbums) {
                return baseList.sortedWith(
                    compareByDescending<AlbumItem> { it.name.equals("Camera", ignoreCase = true) }
                        .thenByDescending { it.coverItem?.dateTakenMs ?: Long.MAX_VALUE }
                )
            }

            // Merge identically-named folders across Internal Storage & External USB-C/NVMe Drive
            val combined = baseList + externalFolders
            return combined
                .groupBy { it.name.trim().lowercase() }
                .map { (_, group) ->
                    if (group.size == 1) {
                        group.first()
                    } else {
                        val primary = group.firstOrNull { it.source == StorageSource.INTERNAL_MEDIASTORE }
                            ?: group.first()
                        val newestCover = group.mapNotNull { it.coverItem }
                            .maxByOrNull { it.dateTakenMs }
                        primary.copy(
                            coverItem = newestCover,
                            itemCount = group.sumOf { it.itemCount },
                            totalSizeBytes = group.sumOf { it.totalSizeBytes },
                            isMerged = true,
                            mergedBucketIds = group.flatMap { it.mergedBucketIds }.toSet(),
                            isCustomEmpty = group.all { it.isCustomEmpty }
                        )
                    }
                }
                .sortedWith(
                    compareByDescending<AlbumItem> { it.name.equals("Camera", ignoreCase = true) }
                        .thenByDescending { it.coverItem?.dateTakenMs ?: Long.MAX_VALUE }
                )
        }

    /**
     * Filtered Albums shown on the main Albums tab honoring Hide Albums and Select Essential Albums.
     */
    val visibleAlbums: List<AlbumItem>
        get() {
            val notHidden = allAvailableAlbums.filterNot { album ->
                album.id in settings.hiddenAlbumIds ||
                    album.mergedBucketIds.any { it in settings.hiddenAlbumIds }
            }
            val essentialFiltered = if (settings.essentialAlbumsEnabled && settings.essentialAlbumIds.isNotEmpty()) {
                notHidden.filter { album ->
                    album.id in settings.essentialAlbumIds || album.isCustomEmpty
                }
            } else {
                notHidden
            }
            val q = searchQuery.trim().lowercase()
            return if (q.isEmpty()) essentialFiltered else essentialFiltered.filter {
                it.name.lowercase().contains(q)
            }
        }

    val activeGridItems: List<MediaItem>
        get() {
            val allExternalMedia = externalDrives.flatMap { it.items }
            val base = when {
                openedAlbum != null -> {
                    val album = openedAlbum
                    if (album.isMerged) {
                        (internalMedia + allExternalMedia).filter {
                            it.bucketId in album.mergedBucketIds ||
                                it.bucketName.equals(album.name, ignoreCase = true)
                        }
                    } else if (album.source == StorageSource.EXTERNAL_SAF) {
                        allExternalMedia.filter { it.bucketId == album.id }
                    } else {
                        internalMedia.filter {
                            it.bucketId == album.id ||
                                it.relativePath.equals(album.relativePath, ignoreCase = true)
                        }
                    }
                }
                openedCollection != null -> when (openedCollection) {
                    SpecialCollection.FAVORITES -> internalMedia.filter { it.isFavorite }
                    SpecialCollection.VIDEOS -> internalMedia.filter { it.isVideo }
                    SpecialCollection.RECENT -> internalMedia.sortedByDescending { it.dateTakenMs }.take(40)
                    SpecialCollection.TRASH -> trashedMedia
                }
                else -> {
                    val visibleInternal = internalMedia.filterNot { it.bucketId in settings.hiddenAlbumIds }
                    when (activeMediaFilter) {
                        SmartMediaFilter.ALL -> visibleInternal
                        SmartMediaFilter.VIDEOS -> visibleInternal.filter { it.isVideo }
                        SmartMediaFilter.SCREENSHOTS -> visibleInternal.filter { it.isScreenshot }
                        SmartMediaFilter.FAVORITES -> visibleInternal.filter { it.isFavorite }
                    }
                }
            }
            val filtered = if (searchQuery.isBlank()) {
                base
            } else {
                val q = searchQuery.trim().lowercase()
                base.filter {
                    it.displayName.lowercase().contains(q) ||
                        it.bucketName.lowercase().contains(q) ||
                        it.relativePath.lowercase().contains(q) ||
                        it.mimeType.lowercase().contains(q)
                }
            }
            return when (sortOrder) {
                SortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateTakenMs }
                SortOrder.DATE_ASC -> filtered.sortedBy { it.dateTakenMs }
                SortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
                SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            }
        }
}

sealed interface PendingSystemRequest {
    data class LaunchIntentSender(val sender: IntentSender, val onSuccessMessage: String) : PendingSystemRequest
    data class ShareUris(val uris: List<Uri>, val mimeType: String) : PendingSystemRequest
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaStoreRepo = MediaStoreRepository(application)
    private val externalDriveRepo = ExternalDriveRepository(application)
    private val prefsRepo = OnegalPreferencesRepository(application)
    private val smartAnalyzer = SmartMediaAnalyzer(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _systemRequests = MutableSharedFlow<PendingSystemRequest>(extraBufferCapacity = 4)
    val systemRequests: SharedFlow<PendingSystemRequest> = _systemRequests.asSharedFlow()

    private var observerStarted = false
    private val inFlightExifIds = HashSet<String>()
    private val inFlightScanIds = HashSet<String>()
    private var cutoutJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepo.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            prefsRepo.customAlbumsFlow.collect { custom ->
                _uiState.update { it.copy(customAlbums = custom) }
            }
        }
        refreshCacheSize()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasMediaPermission = granted) }
        if (granted) {
            refreshAll()
            startMediaObserver()
        }
    }

    @OptIn(FlowPreview::class)
    private fun startMediaObserver() {
        if (observerStarted) return
        observerStarted = true
        viewModelScope.launch {
            mediaStoreRepo.observeMediaChanges()
                .debounce(250L)
                .collect {
                    refreshInternalMedia()
                }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            refreshInternalMedia()
            refreshExternalDrives()
            refreshCacheSize()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshInternalMedia() {
        viewModelScope.launch {
            val active = mediaStoreRepo.loadInternalMedia(includeTrashedOnly = false)
            val trashed = mediaStoreRepo.loadInternalMedia(includeTrashedOnly = true)
            val albums = mediaStoreRepo.buildAlbumsFromMedia(active)
            _uiState.update {
                it.copy(
                    internalMedia = active,
                    trashedMedia = trashed,
                    internalAlbums = albums
                )
            }
        }
    }

    fun refreshExternalDrives() {
        viewModelScope.launch {
            val drives = externalDriveRepo.scanAllMountedDrives()
            _uiState.update {
                it.copy(
                    externalDrives = drives,
                    pendingUsbPromptDevice = if (drives.isNotEmpty()) null else it.pendingUsbPromptDevice
                )
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val bytes = prefsRepo.computeThumbnailCacheBytes()
            _uiState.update { it.copy(thumbnailCacheBytes = bytes) }
        }
    }

    fun clearThumbnailCache() {
        viewModelScope.launch {
            prefsRepo.clearThumbnailCache()
            _uiState.update { it.copy(thumbnailCacheBytes = 0L, toastMessage = "Thumbnail cache cleared") }
        }
    }

    fun updateSettings(transform: (GallerySettings) -> GallerySettings) {
        viewModelScope.launch {
            prefsRepo.updateSettings(transform)
        }
    }

    fun createNewAlbum(
        name: String,
        targetExternalDrive: ExternalDriveInfo?,
        onCreated: ((AlbumItem) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val record = prefsRepo.createPersistentAlbum(name, targetExternalDrive)
            if (record == null) {
                showToast("Enter a valid album name")
                return@launch
            }
            val albumItem = AlbumItem(
                id = record.id,
                name = record.name,
                coverItem = null,
                itemCount = 0,
                totalSizeBytes = 0L,
                source = record.source,
                relativePath = record.relativePath,
                treeUri = record.treeUriString?.let { Uri.parse(it) },
                documentId = record.documentId,
                isCustomEmpty = true
            )
            if (targetExternalDrive != null) {
                refreshExternalDrives()
            }
            showToast("Created album \"${record.name}\"")
            onCreated?.invoke(albumItem)
        }
    }

    fun toggleAlbumHidden(albumId: String) {
        updateSettings { current ->
            val next = if (albumId in current.hiddenAlbumIds) {
                current.hiddenAlbumIds - albumId
            } else {
                current.hiddenAlbumIds + albumId
            }
            current.copy(hiddenAlbumIds = next)
        }
    }

    fun toggleEssentialAlbum(albumId: String) {
        updateSettings { current ->
            val next = if (albumId in current.essentialAlbumIds) {
                current.essentialAlbumIds - albumId
            } else {
                current.essentialAlbumIds + albumId
            }
            current.copy(essentialAlbumIds = next)
        }
    }

    fun onUsbDriveEvent(event: UsbDriveEvent) {
        when (event) {
            is UsbDriveEvent.Attached -> {
                _uiState.update {
                    it.copy(
                        pendingUsbPromptDevice = event.deviceName,
                        toastMessage = "${event.deviceName} detected — scanning external folders"
                    )
                }
                refreshExternalDrives()
            }
            is UsbDriveEvent.MediaMounted -> {
                _uiState.update { it.copy(toastMessage = "External storage mounted") }
                refreshExternalDrives()
            }
            is UsbDriveEvent.Detached,
            is UsbDriveEvent.MediaUnmounted -> {
                _uiState.update { state ->
                    val closeExternalAlbum = state.openedAlbum?.source == StorageSource.EXTERNAL_SAF
                    state.copy(
                        openedAlbum = if (closeExternalAlbum) null else state.openedAlbum,
                        pendingUsbPromptDevice = null,
                        toastMessage = "External drive safely disconnected"
                    )
                }
                refreshExternalDrives()
            }
        }
    }

    fun onExternalTreeGranted(treeUri: Uri) {
        externalDriveRepo.persistTreePermission(treeUri)
        _uiState.update { it.copy(activeTab = GalleryTab.EXTERNAL, pendingUsbPromptDevice = null) }
        refreshExternalDrives()
    }

    fun disconnectSavedTree(treeUri: Uri) {
        externalDriveRepo.releaseTreePermission(treeUri)
        refreshExternalDrives()
    }

    fun selectTab(tab: GalleryTab) {
        if (tab == GalleryTab.MENU) {
            _uiState.update { it.copy(isMenuSheetOpen = true) }
        } else {
            _uiState.update {
                it.copy(
                    activeTab = tab,
                    openedAlbum = null,
                    openedCollection = null,
                    isSelectionMode = false,
                    selectedIds = emptySet()
                )
            }
        }
    }

    fun closeMenuSheet() = _uiState.update { it.copy(isMenuSheetOpen = false) }

    fun openSettings(open: Boolean) {
        if (open) refreshCacheSize()
        _uiState.update {
            it.copy(isSettingsScreenOpen = open, isMenuSheetOpen = false)
        }
    }

    fun setSortOrder(order: SortOrder) = _uiState.update { it.copy(sortOrder = order) }

    fun setActiveMediaFilter(filter: SmartMediaFilter) = _uiState.update {
        it.copy(activeMediaFilter = filter, isSelectionMode = false, selectedIds = emptySet())
    }

    fun setPicturesColumns(columns: Int) = _uiState.update {
        it.copy(picturesColumns = columns.coerceIn(3, 5))
    }

    fun setAlbumsColumns(columns: Int) = _uiState.update {
        it.copy(albumsColumns = columns.coerceIn(2, 4))
    }

    fun toggleSearch(active: Boolean) = _uiState.update {
        it.copy(isSearchActive = active, searchQuery = if (!active) "" else it.searchQuery)
    }

    fun updateSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun openAlbum(album: AlbumItem) = _uiState.update {
        it.copy(openedAlbum = album, openedCollection = null, isSelectionMode = false, selectedIds = emptySet())
    }

    fun openSpecialCollection(collection: SpecialCollection) = _uiState.update {
        it.copy(
            openedCollection = collection,
            openedAlbum = null,
            isMenuSheetOpen = false,
            isSelectionMode = false,
            selectedIds = emptySet()
        )
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        return when {
            state.viewerIndex != null -> {
                closeViewer()
                true
            }
            state.isSettingsScreenOpen -> {
                openSettings(false)
                true
            }
            state.isSelectionMode -> {
                clearSelection()
                true
            }
            state.openedAlbum != null -> {
                _uiState.update { it.copy(openedAlbum = null) }
                true
            }
            state.openedCollection != null -> {
                _uiState.update { it.copy(openedCollection = null) }
                true
            }
            state.isSearchActive -> {
                toggleSearch(false)
                true
            }
            else -> false
        }
    }

    fun enterSelectionMode(initialId: String? = null) = _uiState.update {
        it.copy(
            isSelectionMode = true,
            selectedIds = if (initialId != null) setOf(initialId) else emptySet()
        )
    }

    fun toggleSelection(id: String) = _uiState.update { state ->
        val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
        state.copy(isSelectionMode = next.isNotEmpty(), selectedIds = next)
    }

    fun toggleDateSectionSelection(sectionItems: List<MediaItem>) = _uiState.update { state ->
        if (sectionItems.isEmpty()) return@update state
        val sectionIds = sectionItems.map { it.id }.toSet()
        val allSectionSelected = sectionIds.all { it in state.selectedIds }
        val next = if (allSectionSelected) {
            state.selectedIds - sectionIds
        } else {
            state.selectedIds + sectionIds
        }
        state.copy(isSelectionMode = next.isNotEmpty(), selectedIds = next)
    }

    fun applyDragSelection(ids: Collection<String>, select: Boolean) = _uiState.update { state ->
        if (ids.isEmpty()) return@update state
        val next = if (select) state.selectedIds + ids else state.selectedIds - ids.toSet()
        state.copy(isSelectionMode = true, selectedIds = next)
    }

    fun selectAll(items: List<MediaItem>) = _uiState.update { state ->
        val allIds = items.map { it.id }.toSet()
        val next = if (state.selectedIds.size == allIds.size) emptySet() else allIds
        state.copy(isSelectionMode = true, selectedIds = next)
    }

    fun clearSelection() = _uiState.update { it.copy(isSelectionMode = false, selectedIds = emptySet()) }

    fun openViewer(items: List<MediaItem>, index: Int) {
        val safeIndex = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                viewerItems = items,
                viewerIndex = safeIndex,
                activeCutoutBitmap = null,
                activeCutoutItemId = null,
                isExtractingCutout = false
            )
        }
        prefetchViewerContext(items, safeIndex)
    }

    fun onViewerPageChanged(index: Int) {
        val items = _uiState.value.viewerItems
        if (index !in items.indices) return
        _uiState.update {
            it.copy(
                viewerIndex = index,
                activeCutoutBitmap = null,
                activeCutoutItemId = null,
                isExtractingCutout = false
            )
        }
        prefetchViewerContext(items, index)
    }

    fun closeViewer() {
        cutoutJob?.cancel()
        _uiState.update {
            it.copy(
                viewerIndex = null,
                activeCutoutBitmap = null,
                activeCutoutItemId = null,
                isExtractingCutout = false
            )
        }
    }

    /**
     * Zero-delay background pre-fetching:
     * Immediately populates exifCache for current & adjacent pages so swipe-up EXIF is ready in RAM
     * before the user starts dragging, and runs on-device ML Kit Text/QR scanning on the settled photo.
     */
    private fun prefetchViewerContext(items: List<MediaItem>, centerIndex: Int) {
        val indices = listOf(centerIndex, centerIndex + 1, centerIndex - 1)
            .filter { it in items.indices }
            .distinct()

        for (idx in indices) {
            val target = items[idx]
            if (!_uiState.value.exifCache.containsKey(target.id) && inFlightExifIds.add(target.id)) {
                viewModelScope.launch {
                    try {
                        val details = mediaStoreRepo.readExifDetails(target)
                        _uiState.update { state ->
                            val trimmedCache = if (state.exifCache.size > 96) {
                                state.exifCache.entries.drop(24).associate { it.toPair() }
                            } else {
                                state.exifCache
                            }
                            state.copy(exifCache = trimmedCache + (target.id to details))
                        }
                    } finally {
                        inFlightExifIds.remove(target.id)
                    }
                }
            }
        }

        val currentItem = items.getOrNull(centerIndex) ?: return
        if (!currentItem.isVideo &&
            !_uiState.value.smartScanCache.containsKey(currentItem.id) &&
            inFlightScanIds.add(currentItem.id)
        ) {
            viewModelScope.launch {
                try {
                    val scan = smartAnalyzer.scanTextAndQr(currentItem.uri)
                    _uiState.update { state ->
                        val trimmed = if (state.smartScanCache.size > 48) {
                            state.smartScanCache.entries.drop(12).associate { it.toPair() }
                        } else {
                            state.smartScanCache
                        }
                        state.copy(smartScanCache = trimmed + (currentItem.id to scan))
                    }
                } finally {
                    inFlightScanIds.remove(currentItem.id)
                }
            }
        }
    }

    fun extractSubjectCutout(item: MediaItem) {
        if (item.isVideo) return
        cutoutJob?.cancel()
        _uiState.update {
            it.copy(
                isExtractingCutout = true,
                activeCutoutBitmap = null,
                activeCutoutItemId = item.id
            )
        }
        cutoutJob = viewModelScope.launch {
            val cutout = smartAnalyzer.extractSubjectCutout(item.uri)
            if (cutout != null) {
                _uiState.update {
                    it.copy(
                        isExtractingCutout = false,
                        activeCutoutBitmap = cutout,
                        activeCutoutItemId = item.id
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isExtractingCutout = false,
                        activeCutoutBitmap = null,
                        activeCutoutItemId = null,
                        toastMessage = "No foreground subject detected"
                    )
                }
            }
        }
    }

    fun clearSubjectCutout() {
        cutoutJob?.cancel()
        _uiState.update {
            it.copy(
                isExtractingCutout = false,
                activeCutoutBitmap = null,
                activeCutoutItemId = null
            )
        }
    }

    fun copyActiveCutoutToClipboard(item: MediaItem) {
        val bitmap = _uiState.value.activeCutoutBitmap ?: return
        viewModelScope.launch {
            val copied = smartAnalyzer.copyCutoutToClipboard(bitmap, item.displayName)
            showToast(if (copied) "Subject cutout copied to clipboard" else "Unable to copy cutout")
            clearSubjectCutout()
        }
    }

    fun shareActiveCutout(item: MediaItem) {
        val bitmap = _uiState.value.activeCutoutBitmap ?: return
        viewModelScope.launch {
            val uri = smartAnalyzer.saveCutoutToCacheUri(bitmap, item.displayName)
            if (uri != null) {
                _systemRequests.tryEmit(PendingSystemRequest.ShareUris(listOf(uri), "image/png"))
            } else {
                showToast("Unable to prepare cutout PNG")
            }
            clearSubjectCutout()
        }
    }

    override fun onCleared() {
        super.onCleared()
        smartAnalyzer.close()
    }

    fun shareItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val mime = when {
            items.all { !it.isVideo } -> "image/*"
            items.all { it.isVideo } -> "video/*"
            else -> "*/*"
        }
        _systemRequests.tryEmit(PendingSystemRequest.ShareUris(uris, mime))
    }

    fun toggleFavorite(items: List<MediaItem>) {
        val internal = items.filter { it.source == StorageSource.INTERNAL_MEDIASTORE }
        if (internal.isEmpty()) {
            showToast("Favorites are stored on internal MediaStore items")
            return
        }
        val makeFav = internal.any { !it.isFavorite }
        val sender = mediaStoreRepo.createFavoriteIntentSender(internal.map { it.uri }, makeFav)
        _systemRequests.tryEmit(
            PendingSystemRequest.LaunchIntentSender(
                sender = sender,
                onSuccessMessage = if (makeFav) "Added to Favorites" else "Removed from Favorites"
            )
        )
        clearSelection()
    }

    fun trashOrDeleteItems(items: List<MediaItem>, permanent: Boolean = false) {
        viewModelScope.launch {
            val usePermanent = permanent || !_uiState.value.settings.trashRetention30Days
            val external = items.filter { it.source == StorageSource.EXTERNAL_SAF }
            val internal = items.filter { it.source == StorageSource.INTERNAL_MEDIASTORE }

            var externalDeleted = 0
            external.forEach { item ->
                if (externalDriveRepo.deleteExternalDocument(item)) externalDeleted++
            }
            if (externalDeleted > 0) {
                refreshExternalDrives()
                showToast("Deleted $externalDeleted file(s) from external drive")
            }

            if (internal.isNotEmpty()) {
                val uris = internal.map { it.uri }
                val sender = if (usePermanent) {
                    mediaStoreRepo.createDeleteIntentSender(uris)
                } else {
                    mediaStoreRepo.createTrashIntentSender(uris, trash = true)
                }
                _systemRequests.tryEmit(
                    PendingSystemRequest.LaunchIntentSender(
                        sender = sender,
                        onSuccessMessage = if (usePermanent) "Permanently deleted" else "Moved to Recycle bin"
                    )
                )
            }
            clearSelection()
            if (_uiState.value.viewerIndex != null) closeViewer()
        }
    }

    fun emptyTrash() {
        val trashed = _uiState.value.trashedMedia
        if (trashed.isEmpty()) {
            showToast("Recycle bin is already empty")
            return
        }
        trashOrDeleteItems(trashed, permanent = true)
    }

    fun restoreFromTrash(items: List<MediaItem>) {
        val uris = items.filter { it.source == StorageSource.INTERNAL_MEDIASTORE }.map { it.uri }
        if (uris.isEmpty()) return
        val sender = mediaStoreRepo.createTrashIntentSender(uris, trash = false)
        _systemRequests.tryEmit(
            PendingSystemRequest.LaunchIntentSender(
                sender = sender,
                onSuccessMessage = "Restored ${uris.size} item(s)"
            )
        )
        clearSelection()
    }

    fun renameItem(item: MediaItem, newName: String) {
        viewModelScope.launch {
            val cleaned = newName.trim()
            if (cleaned.isEmpty()) return@launch
            val success = if (item.source == StorageSource.EXTERNAL_SAF) {
                externalDriveRepo.renameExternalDocument(item, cleaned)
            } else {
                mediaStoreRepo.renameInternalMedia(item, cleaned)
            }
            if (success) {
                showToast("Renamed to $cleaned")
                refreshAll()
            } else if (item.source == StorageSource.INTERNAL_MEDIASTORE) {
                val sender = mediaStoreRepo.createWriteIntentSender(listOf(item.uri))
                _systemRequests.tryEmit(
                    PendingSystemRequest.LaunchIntentSender(sender, "Permission granted — retry rename")
                )
            } else {
                showToast("Unable to rename file")
            }
            clearSelection()
        }
    }

    fun copyOrMoveItems(
        items: List<MediaItem>,
        targetAlbum: AlbumItem,
        isMove: Boolean
    ) {
        viewModelScope.launch {
            var copied = 0
            val succeededSources = ArrayList<MediaItem>()
            for (item in items) {
                val destUri = if (targetAlbum.source == StorageSource.EXTERNAL_SAF &&
                    targetAlbum.treeUri != null && targetAlbum.documentId != null
                ) {
                    mediaStoreRepo.copyMediaToExternalFolder(item, targetAlbum.treeUri, targetAlbum.documentId)
                } else {
                    mediaStoreRepo.copyMediaToInternalAlbum(item, targetAlbum.relativePath)
                }
                if (destUri != null) {
                    copied++
                    succeededSources.add(item)
                }
            }
            if (isMove && succeededSources.isNotEmpty()) {
                trashOrDeleteItems(succeededSources, permanent = true)
            }
            showToast(
                if (isMove) "Moved $copied item(s) to ${targetAlbum.name}"
                else "Copied $copied item(s) to ${targetAlbum.name}"
            )
            refreshAll()
            clearSelection()
        }
    }

    fun captureVideoFrame(bitmap: Bitmap, videoName: String) {
        viewModelScope.launch {
            val saved = mediaStoreRepo.saveVideoFramePng(bitmap, videoName)
            showToast(
                if (saved != null) "Frame saved to Pictures/Onegal Captures"
                else "Failed to capture video frame"
            )
            if (saved != null) refreshInternalMedia()
        }
    }

    fun showToast(message: String) = _uiState.update { it.copy(toastMessage = message) }

    fun consumeToast() = _uiState.update { it.copy(toastMessage = null) }

    fun groupByDate(items: List<MediaItem>): List<DateSection> {
        if (items.isEmpty()) return emptyList()
        val nowCal = Calendar.getInstance()
        val todayKey = dayKeyFormat.format(nowCal.time)
        nowCal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = dayKeyFormat.format(nowCal.time)

        val grouped = LinkedHashMap<String, MutableList<MediaItem>>()
        for (item in items) {
            val key = dayKeyFormat.format(Date(item.dateTakenMs))
            grouped.getOrPut(key) { ArrayList() }.add(item)
        }
        return grouped.map { (key, sectionItems) ->
            val sampleDate = Date(sectionItems.first().dateTakenMs)
            val title = when (key) {
                todayKey -> "Today"
                yesterdayKey -> "Yesterday"
                else -> sectionTitleFormat.format(sampleDate)
            }
            DateSection(
                key = key,
                title = title,
                monthBadge = monthBadgeFormat.format(sampleDate),
                items = sectionItems
            )
        }
    }

    companion object {
        private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val sectionTitleFormat = SimpleDateFormat("MMM d", Locale.US)
        private val monthBadgeFormat = SimpleDateFormat("MMM yyyy", Locale.US)
    }
}
