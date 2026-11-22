package com.onegal.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.onegal.gallery.data.model.GalleryTab
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.data.model.SpecialCollection
import com.onegal.gallery.data.model.ThemePreference
import com.onegal.gallery.system.UsbDriveReceiver
import com.onegal.gallery.ui.components.AlbumChecklistDialog
import com.onegal.gallery.ui.components.CreateAlbumDialog
import com.onegal.gallery.ui.components.DestinationPickerDialog
import com.onegal.gallery.ui.components.FloatingNavPill
import com.onegal.gallery.ui.components.MediaGrid
import com.onegal.gallery.ui.components.MultiSelectActionBar
import com.onegal.gallery.ui.components.OneUiCollapsibleHeader
import com.onegal.gallery.ui.components.OneUiMenuBottomSheet
import com.onegal.gallery.ui.components.RenameMediaDialog
import com.onegal.gallery.ui.components.rememberOneUiCollapseState
import com.onegal.gallery.ui.screens.AlbumsScreen
import com.onegal.gallery.ui.screens.ExternalScreen
import com.onegal.gallery.ui.screens.FullScreenViewer
import com.onegal.gallery.ui.screens.SettingsScreen
import com.onegal.gallery.ui.theme.OnegalTheme
import com.onegal.gallery.ui.viewmodel.GalleryViewModel
import com.onegal.gallery.ui.viewmodel.PendingSystemRequest

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var usbReceiver: UsbDriveReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        usbReceiver = UsbDriveReceiver { event ->
            viewModel.onUsbDriveEvent(event)
        }
        UsbDriveReceiver.register(this, usbReceiver)

        setContent {
            val state by viewModel.uiState.collectAsState()
            val isDark = when (state.settings.themePreference) {
                ThemePreference.DARK_AMOLED -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }

            OnegalTheme(preference = state.settings.themePreference) {
                OnegalRootScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDark
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::usbReceiver.isInitialized) {
            UsbDriveReceiver.unregister(this, usbReceiver)
        }
    }
}

@Composable
private fun OnegalRootScreen(
    viewModel: GalleryViewModel,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            grants[Manifest.permission.READ_MEDIA_IMAGES] == true ||
                grants[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            grants[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        viewModel.onPermissionResult(mediaGranted)
    }

    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            viewModel.onExternalTreeGranted(treeUri)
        }
    }

    var pendingIntentSuccessMsg by remember { mutableStateOf<String?>(null) }
    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingIntentSuccessMsg?.let { viewModel.showToast(it) }
            viewModel.refreshInternalMedia()
        }
        pendingIntentSuccessMsg = null
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.systemRequests.collect { request ->
            when (request) {
                is PendingSystemRequest.LaunchIntentSender -> {
                    pendingIntentSuccessMsg = request.onSuccessMessage
                    intentSenderLauncher.launch(IntentSenderRequest.Builder(request.sender).build())
                }
                is PendingSystemRequest.ShareUris -> {
                    val shareIntent = if (request.uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = request.mimeType
                            putExtra(Intent.EXTRA_STREAM, request.uris.first())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = request.mimeType
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(request.uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                }
            }
        }
    }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    BackHandler(
        enabled = state.viewerIndex != null || state.isSettingsScreenOpen || state.isSelectionMode ||
            state.openedAlbum != null || state.openedCollection != null || state.isSearchActive
    ) {
        viewModel.navigateUp()
    }

    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var copyMoveTargets by remember { mutableStateOf<Pair<List<MediaItem>, Boolean>?>(null) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }
    var createAlbumForPendingCopyMove by remember { mutableStateOf(false) }
    var showEssentialAlbumsDialog by remember { mutableStateOf(false) }
    var showHideAlbumsDialog by remember { mutableStateOf(false) }

    val collapseState = rememberOneUiCollapseState()
    val gridItems = state.activeGridItems
    val sections = remember(gridItems) { viewModel.groupByDate(gridItems) }
    val selectedItems = remember(gridItems, state.selectedIds) {
        gridItems.filter { it.id in state.selectedIds }
    }

    val headerTitle = when {
        state.openedAlbum != null -> state.openedAlbum!!.name
        state.openedCollection != null -> state.openedCollection!!.title
        else -> state.activeTab.label
    }
    val headerSubtitle = when {
        state.openedAlbum != null -> "${gridItems.size} items · ${state.openedAlbum!!.relativePath}"
        state.openedCollection != null -> "${gridItems.size} items"
        state.activeTab == GalleryTab.PICTURES -> "${gridItems.size} photos & videos"
        state.activeTab == GalleryTab.ALBUMS -> "${state.visibleAlbums.size} local albums"
        state.activeTab == GalleryTab.EXTERNAL -> {
            if (state.externalDrives.isEmpty()) "Plug in USB-C / NVMe storage"
            else "${state.externalDrives.size} drive(s) · ${state.externalDrives.sumOf { it.items.size }} items"
        }
        else -> "Onegal"
    }

    if (!state.hasMediaPermission) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Onegal",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Allow local photo and video access to browse your library at 120Hz. Onegal works 100% offline with zero AI and zero cloud sync.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(requiredPermissions) },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Allow Media Access", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.nestedScroll(collapseState.nestedScrollConnection)
        ) { _ ->
            Column(modifier = Modifier.fillMaxSize()) {
                OneUiCollapsibleHeader(
                    title = headerTitle,
                    subtitle = headerSubtitle,
                    collapseState = collapseState,
                    showBackButton = state.openedAlbum != null || state.openedCollection != null,
                    showCreateAlbumAction = state.activeTab == GalleryTab.ALBUMS &&
                        state.openedAlbum == null &&
                        state.openedCollection == null,
                    onCreateAlbumClick = {
                        createAlbumForPendingCopyMove = false
                        showCreateAlbumDialog = true
                    },
                    onBackClick = { viewModel.navigateUp() },
                    isSelectionMode = state.isSelectionMode,
                    selectedCount = state.selectedIds.size,
                    totalSelectableCount = gridItems.size,
                    onToggleSelectAll = { viewModel.selectAll(gridItems) },
                    onCancelSelection = { viewModel.clearSelection() },
                    isSearchActive = state.isSearchActive,
                    searchQuery = state.searchQuery,
                    onSearchToggle = { viewModel.toggleSearch(it) },
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    currentColumns = if (state.activeTab == GalleryTab.ALBUMS && state.openedAlbum == null) {
                        state.albumsColumns
                    } else {
                        state.picturesColumns
                    },
                    onCycleColumns = {
                        if (state.activeTab == GalleryTab.ALBUMS && state.openedAlbum == null) {
                            val next = if (state.albumsColumns >= 4) 2 else state.albumsColumns + 1
                            viewModel.setAlbumsColumns(next)
                        } else {
                            val next = if (state.picturesColumns >= 5) 3 else state.picturesColumns + 1
                            viewModel.setPicturesColumns(next)
                        }
                    },
                    currentSort = state.sortOrder,
                    onSortSelected = { viewModel.setSortOrder(it) },
                    onEnterSelection = { viewModel.enterSelectionMode() },
                    onOpenSettings = { viewModel.openSettings(true) },
                    isDarkTheme = isDarkTheme,
                    showFilterChips = state.activeTab == GalleryTab.PICTURES &&
                        state.openedAlbum == null &&
                        state.openedCollection == null &&
                        !state.isSelectionMode,
                    activeFilter = state.activeMediaFilter,
                    onFilterSelected = { viewModel.setActiveMediaFilter(it) }
                )

                Box(modifier = Modifier.weight(1f)) {
                    when {
                        state.openedAlbum != null || state.openedCollection != null || state.activeTab == GalleryTab.PICTURES -> {
                            MediaGrid(
                                sections = sections,
                                allItemsFlat = gridItems,
                                columns = state.picturesColumns,
                                isDarkTheme = isDarkTheme,
                                originalAspectRatio = state.settings.originalAspectRatioGrid,
                                showFormatBadges = state.settings.showFormatBadges,
                                onColumnsChanged = { viewModel.setPicturesColumns(it) },
                                isSelectionMode = state.isSelectionMode,
                                selectedIds = state.selectedIds,
                                onItemClick = { item, index ->
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        viewModel.openViewer(gridItems, index)
                                    }
                                },
                                onItemLongClick = { item ->
                                    if (!state.isSelectionMode) {
                                        viewModel.enterSelectionMode(item.id)
                                    } else {
                                        viewModel.toggleSelection(item.id)
                                    }
                                },
                                onToggleDateSection = { sectionItems ->
                                    viewModel.toggleDateSectionSelection(sectionItems)
                                },
                                onApplyDragSelection = { sweptIds, shouldSelect ->
                                    viewModel.applyDragSelection(sweptIds, shouldSelect)
                                }
                            )
                        }
                        state.activeTab == GalleryTab.ALBUMS -> {
                            AlbumsScreen(
                                albums = state.visibleAlbums,
                                columns = state.albumsColumns,
                                onColumnsChanged = { viewModel.setAlbumsColumns(it) },
                                onAlbumClick = { viewModel.openAlbum(it) }
                            )
                        }
                        state.activeTab == GalleryTab.EXTERNAL -> {
                            ExternalScreen(
                                drives = state.externalDrives,
                                pendingUsbPromptDevice = state.pendingUsbPromptDevice,
                                onConnectDriveClick = { openTreeLauncher.launch(null) },
                                onRefreshClick = { viewModel.refreshExternalDrives() },
                                onSafelyEjectClick = { UsbDriveReceiver.openAndroidStorageSettings(context) },
                                onForgetDriveClick = { viewModel.disconnectSavedTree(it) },
                                onFolderClick = { viewModel.openAlbum(it) }
                            )
                        }
                        else -> Unit
                    }

                    // Bottom floating navigation pill or multi-select action bar
                    if (state.isSelectionMode) {
                        MultiSelectActionBar(
                            isDarkTheme = isDarkTheme,
                            isTrashView = state.openedCollection == SpecialCollection.TRASH,
                            selectedCount = selectedItems.size,
                            onShare = { viewModel.shareItems(selectedItems) },
                            onCopy = { copyMoveTargets = selectedItems to false },
                            onMove = { copyMoveTargets = selectedItems to true },
                            onRename = { selectedItems.firstOrNull()?.let { renameTarget = it } },
                            onFavorite = { viewModel.toggleFavorite(selectedItems) },
                            onDelete = {
                                viewModel.trashOrDeleteItems(
                                    selectedItems,
                                    permanent = state.openedCollection == SpecialCollection.TRASH
                                )
                            },
                            onRestore = { viewModel.restoreFromTrash(selectedItems) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    } else if (state.openedAlbum == null && state.openedCollection == null) {
                        FloatingNavPill(
                            activeTab = state.activeTab,
                            isDarkTheme = isDarkTheme,
                            hasExternalDrive = state.externalDrives.isNotEmpty(),
                            onTabSelected = { viewModel.selectTab(it) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        // Full-Screen One UI 8.0 Gallery Settings Screen
        if (state.isSettingsScreenOpen) {
            SettingsScreen(
                settings = state.settings,
                isDarkTheme = isDarkTheme,
                hasMediaPermission = state.hasMediaPermission,
                trashedCount = state.trashedMedia.size,
                hiddenAlbumCount = state.settings.hiddenAlbumIds.size,
                essentialAlbumCount = state.settings.essentialAlbumIds.size,
                thumbnailCacheBytes = state.thumbnailCacheBytes,
                onBack = { viewModel.openSettings(false) },
                onUpdateSettings = { viewModel.updateSettings(it) },
                onOpenEssentialAlbumsPicker = { showEssentialAlbumsDialog = true },
                onOpenHideAlbumsPicker = { showHideAlbumsDialog = true },
                onEmptyTrash = { viewModel.emptyTrash() },
                onClearThumbnailCache = { viewModel.clearThumbnailCache() }
            )
        }

        // Full-Screen Media Viewer overlay
        val viewerIdx = state.viewerIndex
        if (viewerIdx != null) {
            FullScreenViewer(
                items = state.viewerItems,
                initialIndex = viewerIdx,
                exifCache = state.exifCache,
                smartScanCache = state.smartScanCache,
                activeCutoutBitmap = state.activeCutoutBitmap,
                activeCutoutItemId = state.activeCutoutItemId,
                isExtractingCutout = state.isExtractingCutout,
                autoPlayVideos = state.settings.autoPlayVideos,
                fullScreenScrolling = state.settings.fullScreenScrolling,
                showFullExifLocation = state.settings.showFullExifLocation,
                onPageChanged = { viewModel.onViewerPageChanged(it) },
                onDismiss = { viewModel.closeViewer() },
                onFavoriteClick = { viewModel.toggleFavorite(listOf(it)) },
                onShareClick = { viewModel.shareItems(listOf(it)) },
                onCopyClick = { copyMoveTargets = listOf(it) to false },
                onRenameClick = { renameTarget = it },
                onDeleteClick = {
                    viewModel.trashOrDeleteItems(
                        listOf(it),
                        permanent = state.openedCollection == SpecialCollection.TRASH
                    )
                },
                onCaptureFrame = { bitmap, name -> viewModel.captureVideoFrame(bitmap, name) },
                onLongPressSubject = { viewModel.extractSubjectCutout(it) },
                onCopyCutout = { viewModel.copyActiveCutoutToClipboard(it) },
                onShareCutout = { viewModel.shareActiveCutout(it) },
                onDismissCutout = { viewModel.clearSubjectCutout() },
                onCopyTextToast = { viewModel.showToast(it) }
            )
        }
    }

    if (state.isMenuSheetOpen) {
        OneUiMenuBottomSheet(
            isDarkTheme = isDarkTheme,
            favoritesCount = state.internalMedia.count { it.isFavorite },
            videosCount = state.internalMedia.count { it.isVideo },
            trashCount = state.trashedMedia.size,
            externalDrivesCount = state.externalDrives.size,
            onSelectCollection = { viewModel.openSpecialCollection(it) },
            onOpenExternal = {
                viewModel.closeMenuSheet()
                viewModel.selectTab(GalleryTab.EXTERNAL)
            },
            onCreateAlbum = {
                viewModel.closeMenuSheet()
                createAlbumForPendingCopyMove = false
                showCreateAlbumDialog = true
            },
            onOpenHideAlbums = {
                viewModel.closeMenuSheet()
                showHideAlbumsDialog = true
            },
            onOpenSettings = { viewModel.openSettings(true) },
            onDismiss = { viewModel.closeMenuSheet() }
        )
    }

    if (showCreateAlbumDialog) {
        CreateAlbumDialog(
            externalDrives = state.externalDrives,
            onConfirm = { name, targetDrive ->
                val pending = if (createAlbumForPendingCopyMove) copyMoveTargets else null
                showCreateAlbumDialog = false
                createAlbumForPendingCopyMove = false
                viewModel.createNewAlbum(name, targetDrive) { createdAlbum ->
                    if (pending != null) {
                        viewModel.copyOrMoveItems(pending.first, createdAlbum, pending.second)
                        copyMoveTargets = null
                    }
                }
            },
            onDismiss = {
                showCreateAlbumDialog = false
                createAlbumForPendingCopyMove = false
            }
        )
    }

    if (showEssentialAlbumsDialog) {
        AlbumChecklistDialog(
            title = "Select essential albums",
            subtitle = "Checked albums remain visible on the Albums tab when Essential mode is enabled.",
            albums = state.allAvailableAlbums,
            checkedIds = state.settings.essentialAlbumIds,
            onToggleAlbum = { viewModel.toggleEssentialAlbum(it) },
            onDismiss = { showEssentialAlbumsDialog = false }
        )
    }

    if (showHideAlbumsDialog) {
        AlbumChecklistDialog(
            title = "Hide albums",
            subtitle = "Checked albums are hidden from Pictures and Albums without deleting any files.",
            albums = state.allAvailableAlbums,
            checkedIds = state.settings.hiddenAlbumIds,
            onToggleAlbum = { viewModel.toggleAlbumHidden(it) },
            onDismiss = { showHideAlbumsDialog = false }
        )
    }

    renameTarget?.let { target ->
        RenameMediaDialog(
            item = target,
            onConfirm = { newName ->
                viewModel.renameItem(target, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    copyMoveTargets?.let { (targets, isMove) ->
        val allDestinations = remember(state.allAvailableAlbums, state.externalDrives) {
            (state.allAvailableAlbums + state.externalDrives.flatMap { it.folders })
                .distinctBy { "${it.source}_${it.id}" }
        }
        DestinationPickerDialog(
            isMove = isMove,
            itemCount = targets.size,
            destinations = allDestinations,
            onCreateNewAlbumClick = {
                createAlbumForPendingCopyMove = true
                showCreateAlbumDialog = true
            },
            onDestinationSelected = { dest ->
                viewModel.copyOrMoveItems(targets, dest, isMove)
                copyMoveTargets = null
            },
            onDismiss = { copyMoveTargets = null }
        )
    }
}
