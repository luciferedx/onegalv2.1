package com.onegal.gallery.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.onegal.gallery.data.model.ExifDetails
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.data.model.SmartMediaScanResult
import com.onegal.gallery.ui.components.formatDurationMs
import com.onegal.gallery.ui.theme.OneUiColors
import com.onegal.gallery.ui.theme.OneUiMotion
import com.onegal.gallery.ui.theme.oneUiGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Full-screen One UI 8.0 Media Viewer:
 * - Zero-delay 1:1 interactive swipe-up frosted-glass EXIF details card backed by pre-fetched exifCache
 * - Rubber-band swipe-down to dismiss with real-time scale, finger offset, and #000000 backdrop fade
 * - Interactive bottom filmstrip thumbnail scrubber with real-time HorizontalPager scrubbing & haptics
 * - On-device ML Kit Subject Lift (long-press cutout glow + Copy Cutout / Share PNG pill)
 * - On-device ML Kit Live Text & QR code corner action pill
 */
@Composable
fun FullScreenViewer(
    items: List<MediaItem>,
    initialIndex: Int,
    exifCache: Map<String, ExifDetails>,
    smartScanCache: Map<String, SmartMediaScanResult>,
    activeCutoutBitmap: Bitmap?,
    activeCutoutItemId: String?,
    isExtractingCutout: Boolean,
    autoPlayVideos: Boolean,
    fullScreenScrolling: Boolean,
    showFullExifLocation: Boolean,
    onPageChanged: (Int) -> Unit,
    onDismiss: () -> Unit,
    onFavoriteClick: (MediaItem) -> Unit,
    onShareClick: (MediaItem) -> Unit,
    onCopyClick: (MediaItem) -> Unit,
    onRenameClick: (MediaItem) -> Unit,
    onDeleteClick: (MediaItem) -> Unit,
    onCaptureFrame: (Bitmap, String) -> Unit,
    onLongPressSubject: (MediaItem) -> Unit,
    onCopyCutout: (MediaItem) -> Unit,
    onShareCutout: (MediaItem) -> Unit,
    onDismissCutout: () -> Unit,
    onCopyTextToast: (String) -> Unit
) {
    if (items.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )

    var showChrome by remember { mutableStateOf(true) }
    var isCurrentItemZoomed by remember { mutableStateOf(false) }
    var showDetectedTextCard by remember { mutableStateOf(false) }

    // 1:1 interactive vertical gesture state:
    // dismissOffsetY >= 0f drives rubber-band swipe-down dismiss;
    // detailsRevealFraction in [0f..1f] drives 1:1 swipe-up frosted-glass EXIF sheet under the finger.
    val dismissOffsetY = remember { Animatable(0f) }
    val detailsRevealFraction = remember { Animatable(0f) }
    var lastVerticalVelocityHint by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            isCurrentItemZoomed = false
            showDetectedTextCard = false
            onPageChanged(page)
        }
    }

    val currentItem = items.getOrElse(pagerState.currentPage) { items.first() }
    val currentExif = exifCache[currentItem.id]
    val currentSmartScan = smartScanCache[currentItem.id]
    val hasActiveCutout = activeCutoutBitmap != null && activeCutoutItemId == currentItem.id

    val dismissProgress = (dismissOffsetY.value / 420f).coerceIn(0f, 1f)
    val scaleOnDismiss = (1f - dismissProgress * 0.30f).coerceIn(0.70f, 1f)
    val backdropAlpha = (1f - dismissProgress * 0.88f).coerceIn(0.12f, 1f)
    val detailsProgress = detailsRevealFraction.value.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha))
    ) {
        val maxSheetHeightPx = with(density) { (maxHeight * 0.58f).coerceAtLeast(320.dp).toPx() }
        val maxSheetHeightDp = (maxHeight * 0.58f).coerceAtLeast(320.dp)

        val pagerBaseModifier = if (fullScreenScrolling) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentItemZoomed && detailsProgress < 0.05f && !hasActiveCutout,
            beyondViewportPageCount = 2,
            modifier = pagerBaseModifier
                .graphicsLayer {
                    val sheetLiftPx = -detailsProgress * (maxSheetHeightPx * 0.24f)
                    val sheetScale = 1f - (detailsProgress * 0.08f)
                    translationY = dismissOffsetY.value + sheetLiftPx
                    scaleX = scaleOnDismiss * sheetScale
                    scaleY = scaleOnDismiss * sheetScale
                }
                .pointerInput(isCurrentItemZoomed, hasActiveCutout, maxSheetHeightPx) {
                    if (!isCurrentItemZoomed && !hasActiveCutout) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                lastVerticalVelocityHint = dragAmount
                                scope.launch {
                                    when {
                                        // If EXIF details sheet is partially/fully visible OR user is dragging upward from rest:
                                        detailsRevealFraction.value > 0f || (dismissOffsetY.value <= 0f && dragAmount < 0f) -> {
                                            val deltaFraction = -dragAmount / maxSheetHeightPx
                                            val nextFraction = (detailsRevealFraction.value + deltaFraction).coerceIn(0f, 1f)
                                            detailsRevealFraction.snapTo(nextFraction)
                                            if (nextFraction == 0f && dragAmount > 0f) {
                                                dismissOffsetY.snapTo((dismissOffsetY.value + dragAmount).coerceAtLeast(0f))
                                            }
                                        }
                                        // Otherwise rubber-band swipe-down dismiss tracking 1:1 with finger
                                        else -> {
                                            val nextDismiss = (dismissOffsetY.value + dragAmount).coerceAtLeast(0f)
                                            dismissOffsetY.snapTo(nextDismiss)
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        detailsRevealFraction.value > 0f -> {
                                            val shouldOpen = detailsRevealFraction.value > 0.24f ||
                                                (detailsRevealFraction.value > 0.08f && lastVerticalVelocityHint < -18f)
                                            detailsRevealFraction.animateTo(
                                                targetValue = if (shouldOpen) 1f else 0f,
                                                animationSpec = OneUiMotion.rubberBandSpring()
                                            )
                                        }
                                        dismissOffsetY.value > 165f || (dismissOffsetY.value > 72f && lastVerticalVelocityHint > 26f) -> {
                                            onDismiss()
                                        }
                                        else -> {
                                            dismissOffsetY.animateTo(0f, OneUiMotion.rubberBandSpring())
                                        }
                                    }
                                    lastVerticalVelocityHint = 0f
                                }
                            },
                            onDragCancel = {
                                lastVerticalVelocityHint = 0f
                                scope.launch {
                                    dismissOffsetY.animateTo(0f, OneUiMotion.rubberBandSpring())
                                    detailsRevealFraction.animateTo(
                                        if (detailsRevealFraction.value > 0.5f) 1f else 0f,
                                        OneUiMotion.rubberBandSpring()
                                    )
                                }
                            }
                        )
                    }
                }
        ) { page ->
            val item = items[page]
            val isActivePage = page == pagerState.currentPage
            if (item.isVideo) {
                Media3VideoPage(
                    item = item,
                    isActivePage = isActivePage,
                    autoPlay = autoPlayVideos,
                    showControls = showChrome && detailsProgress < 0.08f,
                    onToggleControls = { showChrome = !showChrome },
                    onCaptureFrame = { bitmap -> onCaptureFrame(bitmap, item.displayName) }
                )
            } else {
                ZoomablePhotoPage(
                    item = item,
                    onTap = {
                        when {
                            hasActiveCutout -> onDismissCutout()
                            detailsProgress > 0.05f -> {
                                scope.launch {
                                    detailsRevealFraction.animateTo(0f, OneUiMotion.rubberBandSpring())
                                }
                            }
                            else -> showChrome = !showChrome
                        }
                    },
                    onLongPress = {
                        if (!isCurrentItemZoomed && detailsProgress < 0.05f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressSubject(item)
                        }
                    },
                    onZoomStateChanged = { zoomed ->
                        if (isActivePage) isCurrentItemZoomed = zoomed
                    }
                )
            }
        }

        // ML Kit Subject Lift Cutout Overlay (subtle white edge glow + floating monochrome action pill)
        if (isExtractingCutout && activeCutoutItemId == currentItem.id) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .oneUiGlass(isDarkTheme = true, cornerRadius = 22.dp, alpha = 0.80f)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Lifting subject…",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        if (hasActiveCutout && activeCutoutBitmap != null) {
            SubjectCutoutOverlay(
                cutoutBitmap = activeCutoutBitmap,
                onCopyCutout = { onCopyCutout(currentItem) },
                onShareCutout = { onShareCutout(currentItem) },
                onDismiss = onDismissCutout
            )
        }

        // Top floating frosted-glass toolbar
        AnimatedVisibility(
            visible = showChrome && dismissProgress < 0.08f && detailsProgress < 0.25f && !hasActiveCutout,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .oneUiGlass(isDarkTheme = true, cornerRadius = 24.dp, alpha = 0.80f)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Close viewer",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatViewerTimestamp(currentItem.dateTakenMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentItem.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = OneUiColors.DarkTertiaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            val target = if (detailsRevealFraction.value > 0.5f) 0f else 1f
                            detailsRevealFraction.animateTo(target, OneUiMotion.rubberBandSpring())
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Media details",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom stack: Live Text / QR pill + Interactive Filmstrip Scrubber + Photo Action Bar
        AnimatedVisibility(
            visible = showChrome && dismissProgress < 0.08f && detailsProgress < 0.15f && !hasActiveCutout,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live Text & QR Corner Action Pill (appears automatically when ML Kit detects content)
                if (!currentItem.isVideo && currentSmartScan?.hasActionableContent == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val qrUrl = currentSmartScan.qrUrl
                        val qrRaw = currentSmartScan.qrRawValue
                        val detectedText = currentSmartScan.detectedText

                        if (!qrUrl.isNullOrBlank()) {
                            SmartActionCornerPill(
                                icon = Icons.Rounded.QrCode2,
                                label = "Open QR Link",
                                trailingIcon = Icons.Rounded.OpenInNew,
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(qrUrl)))
                                    }.onFailure {
                                        clipboard.setText(AnnotatedString(qrUrl))
                                        onCopyTextToast("QR link copied")
                                    }
                                }
                            )
                        } else if (!qrRaw.isNullOrBlank()) {
                            SmartActionCornerPill(
                                icon = Icons.Rounded.QrCode2,
                                label = "Copy QR",
                                onClick = {
                                    clipboard.setText(AnnotatedString(qrRaw))
                                    onCopyTextToast("QR content copied")
                                }
                            )
                        }

                        if (!detectedText.isNullOrBlank()) {
                            if (!qrUrl.isNullOrBlank() || !qrRaw.isNullOrBlank()) {
                                Spacer(Modifier.width(8.dp))
                            }
                            SmartActionCornerPill(
                                icon = Icons.Rounded.TextFields,
                                label = "Live Text",
                                onClick = { showDetectedTextCard = !showDetectedTextCard }
                            )
                        }
                    }
                }

                // Expandable monochrome Live Text selection/copy card
                if (showDetectedTextCard && !currentSmartScan?.detectedText.isNullOrBlank()) {
                    val textContent = currentSmartScan?.detectedText.orEmpty()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .oneUiGlass(isDarkTheme = true, cornerRadius = 22.dp, alpha = 0.86f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Detected text",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(textContent))
                                    onCopyTextToast("Detected text copied")
                                    showDetectedTextCard = false
                                }
                            ) {
                                Text(
                                    text = "Copy all",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { showDetectedTextCard = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close detected text",
                                    tint = OneUiColors.DarkTertiaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SelectionContainer {
                            Text(
                                text = textContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.90f),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Minimalist Interactive Bottom Filmstrip Scrubber
                if (items.size > 1 && !currentItem.isVideo) {
                    InteractiveFilmstripScrubber(
                        items = items,
                        currentIndex = pagerState.currentPage,
                        onScrubToIndex = { targetIndex ->
                            if (targetIndex != pagerState.currentPage && targetIndex in items.indices) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    pagerState.scrollToPage(targetIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                // Photo frosted-glass action pill (videos render their own integrated Media3 deck)
                if (!currentItem.isVideo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .oneUiGlass(isDarkTheme = true, cornerRadius = 28.dp, alpha = 0.80f)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onFavoriteClick(currentItem) }) {
                            Icon(
                                imageVector = if (currentItem.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { onShareClick(currentItem) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White)
                        }
                        IconButton(onClick = { onCopyClick(currentItem) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = Color.White)
                        }
                        IconButton(onClick = { onRenameClick(currentItem) }) {
                            Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename", tint = Color.White)
                        }
                        IconButton(onClick = { onDeleteClick(currentItem) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Zero-Delay 1:1 Interactive Swipe-Up Frosted-Glass EXIF Details Sheet
        if (detailsProgress > 0.002f) {
            InteractiveExifDetailsSheet(
                item = currentItem,
                exif = currentExif,
                showFullExifLocation = showFullExifLocation,
                revealFraction = detailsProgress,
                maxSheetHeightPx = maxSheetHeightPx,
                onDragFractionDelta = { delta ->
                    scope.launch {
                        detailsRevealFraction.snapTo((detailsRevealFraction.value + delta).coerceIn(0f, 1f))
                    }
                },
                onDragSettled = { velocityHint ->
                    scope.launch {
                        val stayOpen = detailsRevealFraction.value > 0.55f && velocityHint <= 22f
                        detailsRevealFraction.animateTo(
                            targetValue = if (stayOpen) 1f else 0f,
                            animationSpec = OneUiMotion.rubberBandSpring()
                        )
                    }
                },
                onClose = {
                    scope.launch {
                        detailsRevealFraction.animateTo(0f, OneUiMotion.rubberBandSpring())
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeightDp)
                    .graphicsLayer {
                        translationY = (1f - detailsProgress) * maxSheetHeightPx
                    }
            )
        }
    }
}

@Composable
private fun InteractiveFilmstripScrubber(
    items: List<MediaItem>,
    currentIndex: Int,
    onScrubToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
    var scrubAccumulatorPx by remember { mutableFloatStateOf(0f) }
    var lastScrubbedIndex by remember { mutableIntStateOf(currentIndex) }

    LaunchedEffect(currentIndex) {
        lastScrubbedIndex = currentIndex
        if (currentIndex in items.indices) {
            listState.animateScrollToItem((currentIndex - 3).coerceAtLeast(0))
        }
    }

    val thumbRequestSize = remember { Size(128, 128) }
    val stepPx = with(LocalDensity.current) { 34.dp.toPx() }

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .oneUiGlass(isDarkTheme = true, cornerRadius = 20.dp, alpha = 0.80f)
            .pointerInput(items.size, stepPx) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubAccumulatorPx = 0f
                        lastScrubbedIndex = currentIndex
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scrubAccumulatorPx += dragAmount
                        val deltaSteps = (scrubAccumulatorPx / stepPx).roundToInt()
                        if (deltaSteps != 0) {
                            val target = (lastScrubbedIndex + deltaSteps).coerceIn(0, items.lastIndex)
                            if (target != lastScrubbedIndex) {
                                lastScrubbedIndex = target
                                scrubAccumulatorPx = 0f
                                onScrubToIndex(target)
                            }
                        }
                    },
                    onDragEnd = { scrubAccumulatorPx = 0f },
                    onDragCancel = { scrubAccumulatorPx = 0f }
                )
            }
            .padding(vertical = 7.dp)
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> "strip_${item.id}" }
            ) { index, item ->
                val isActive = index == currentIndex
                val thumbScale by animateFloatAsState(
                    targetValue = if (isActive) 1.18f else 0.94f,
                    animationSpec = OneUiMotion.standardSpring(),
                    label = "filmstripScale"
                )
                val request = remember(item.uri) {
                    ImageRequest.Builder(context)
                        .data(item.uri)
                        .size(thumbRequestSize)
                        .build()
                }
                Box(
                    modifier = Modifier
                        .size(width = if (isActive) 34.dp else 28.dp, height = 40.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .clip(RoundedCornerShape(7.dp))
                        .background(OneUiColors.DarkSettingsCard)
                        .border(
                            width = if (isActive) 1.5.dp else 0.75.dp,
                            color = if (isActive) Color.White else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { onScrubToIndex(index) }
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectCutoutOverlay(
    cutoutBitmap: Bitmap,
    onCopyCutout: () -> Unit,
    onShareCutout: () -> Unit,
    onDismiss: () -> Unit
) {
    val imageBitmap = remember(cutoutBitmap) { cutoutBitmap.asImageBitmap() }
    val infiniteTransition = rememberInfiniteTransition(label = "subjectGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 96.dp),
            contentAlignment = Alignment.Center
        ) {
            // Subtle white edge halo behind lifted cutout
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.045f
                        scaleY = 1.045f
                        alpha = glowAlpha * 0.45f
                    }
                    .shadow(22.dp, RoundedCornerShape(16.dp), ambientColor = Color.White, spotColor = Color.White)
            )
            Image(
                bitmap = imageBitmap,
                contentDescription = "Lifted subject cutout",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.03f
                        scaleY = 1.03f
                    }
            )
        }

        // Compact monochrome floating action pill ("Copy Cutout", "Share PNG")
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .oneUiGlass(isDarkTheme = true, cornerRadius = 26.dp, alpha = 0.84f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                modifier = Modifier.clickable(onClick = onCopyCutout)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Copy Cutout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.Black
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, OneUiColors.DarkSpecularBorder),
                modifier = Modifier.clickable(onClick = onShareCutout)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Share PNG",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss cutout",
                    tint = OneUiColors.DarkTertiaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SmartActionCornerPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .oneUiGlass(isDarkTheme = true, cornerRadius = 18.dp, alpha = 0.80f)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = OneUiColors.DarkTertiaryText,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun InteractiveExifDetailsSheet(
    item: MediaItem,
    exif: ExifDetails?,
    showFullExifLocation: Boolean,
    revealFraction: Float,
    maxSheetHeightPx: Float,
    onDragFractionDelta: (Float) -> Unit,
    onDragSettled: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastDelta by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .oneUiGlass(isDarkTheme = true, cornerRadius = 26.dp, alpha = 0.84f)
            .pointerInput(maxSheetHeightPx) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        lastDelta = dragAmount
                        onDragFractionDelta(-dragAmount / maxSheetHeightPx)
                    },
                    onDragEnd = {
                        onDragSettled(lastDelta)
                        lastDelta = 0f
                    },
                    onDragCancel = {
                        onDragSettled(0f)
                        lastDelta = 0f
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Minimalist drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.28f))
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close details",
                    tint = OneUiColors.DarkTertiaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            DetailRow("File name", item.displayName)
            DetailRow("Date & time", formatViewerTimestamp(item.dateTakenMs))
            val resolvedWidth = exif?.width?.takeIf { it > 0 } ?: item.width
            val resolvedHeight = exif?.height?.takeIf { it > 0 } ?: item.height
            DetailRow(
                "Resolution",
                if (resolvedWidth > 0 && resolvedHeight > 0) "$resolvedWidth × $resolvedHeight px" else "Auto-detected"
            )
            DetailRow("File size", formatBytes(item.sizeBytes))
            if (item.isVideo && item.durationMs > 0L) {
                DetailRow("Duration", formatDurationMs(item.durationMs))
            }
            DetailRow("Storage path", "${item.relativePath}${item.displayName}")

            if (showFullExifLocation && exif != null) {
                val cameraLabel = listOfNotNull(exif.cameraMake, exif.cameraModel).joinToString(" ").ifBlank { null }
                val hasCameraData = cameraLabel != null || exif.aperture != null ||
                    exif.exposureTime != null || exif.iso != null || exif.focalLength != null
                if (hasCameraData) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    )
                    cameraLabel?.let { DetailRow("Device model", it) }
                    exif.lensModel?.let { DetailRow("Lens", it) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        exif.iso?.let { ExifChip(it) }
                        exif.aperture?.let { ExifChip(it) }
                        exif.exposureTime?.let { ExifChip(it) }
                        exif.focalLength?.let { ExifChip(it) }
                    }
                    exif.gpsCoordinates?.let { DetailRow("GPS", it) }
                }
            } else if (!showFullExifLocation) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = Color.White.copy(alpha = 0.12f)
                )
                Text(
                    text = "Full EXIF & GPS location hidden (configurable in Gallery settings)",
                    style = MaterialTheme.typography.labelMedium,
                    color = OneUiColors.DarkSecondaryText
                )
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ExifChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

@Composable
private fun ZoomablePhotoPage(
    item: MediaItem,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onZoomStateChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    val imageRequest = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomStateChanged(false)
                        } else {
                            val target = 3f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            scale = target
                            offset = (center - tapOffset) * (target - 1f)
                            onZoomStateChanged(true)
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount >= 2 || scale > 1.02f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val nextScale = (scale * zoomChange).coerceIn(1f, 10f)
                            val maxX = ((size.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((size.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                            val nextOffset = if (nextScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                    y = (offset.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            }
                            scale = nextScale
                            offset = nextOffset
                            onZoomStateChanged(nextScale > 1.02f)
                            if (pointerCount >= 2 || nextScale > 1.02f) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Composable
private fun Media3VideoPage(
    item: MediaItem,
    isActivePage: Boolean,
    autoPlay: Boolean,
    showControls: Boolean,
    onToggleControls: () -> Unit,
    onCaptureFrame: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = autoPlay && isActivePage
        }
    }

    var isPlaying by remember { mutableStateOf(autoPlay && isActivePage) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(isActivePage) {
        if (isActivePage) {
            if (autoPlay) player.play()
        } else {
            player.pause()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.duration > 0L) durationMs = player.duration
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isActivePage) {
        while (true) {
            if (isActivePage && !isScrubbing) {
                currentPosMs = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0L) durationMs = player.duration
            }
            delay(80L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onToggleControls)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerViewRef = this
                }
            },
            update = { view ->
                view.player = player
                playerViewRef = view
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .oneUiGlass(isDarkTheme = true, cornerRadius = 26.dp, alpha = 0.80f)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (isScrubbing) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "${formatDurationMs(scrubPositionMs)} / ${formatDurationMs(durationMs)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                val sliderPos = if (isScrubbing) scrubPositionMs else currentPosMs
                Slider(
                    value = sliderPos.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { next ->
                        isScrubbing = true
                        scrubPositionMs = next.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(scrubPositionMs)
                        currentPosMs = scrubPositionMs
                        isScrubbing = false
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(sliderPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    Text(
                        text = formatDurationMs(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = OneUiColors.DarkSecondaryText
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = {
                        isMuted = !isMuted
                        player.volume = if (isMuted) 0f else 1f
                    }) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White
                        )
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.clickable { speedMenuExpanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = "Playback speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "${playbackSpeed}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = speedMenuExpanded,
                            onDismissRequest = { speedMenuExpanded = false },
                            containerColor = OneUiColors.DarkGlassBase,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${speed}x",
                                            fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                            color = if (speed == playbackSpeed) Color.White else OneUiColors.DarkSecondaryText
                                        )
                                    },
                                    onClick = {
                                        playbackSpeed = speed
                                        player.playbackParameters = PlaybackParameters(speed)
                                        speedMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        modifier = Modifier.clickable {
                            val capturePosUs = player.currentPosition * 1000L
                            scope.launch {
                                val surfaceBitmap = (playerViewRef?.videoSurfaceView as? TextureView)?.bitmap
                                val bitmap = surfaceBitmap ?: withContext(Dispatchers.IO) {
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(context, item.uri)
                                        retriever.getFrameAtTime(
                                            capturePosUs,
                                            MediaMetadataRetriever.OPTION_CLOSEST
                                        )
                                    } catch (_: Exception) {
                                        null
                                    } finally {
                                        retriever.release()
                                    }
                                }
                                if (bitmap != null) {
                                    onCaptureFrame(bitmap)
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Capture frame",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Capture",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OneUiColors.DarkSecondaryText
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}

private fun formatViewerTimestamp(epochMs: Long): String {
    return SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.US).format(Date(epochMs))
}
