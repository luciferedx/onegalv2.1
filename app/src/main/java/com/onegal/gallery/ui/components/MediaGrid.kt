package com.onegal.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.onegal.gallery.data.model.DateSection
import com.onegal.gallery.data.model.MediaItem
import com.onegal.gallery.ui.theme.OneUiColors
import com.onegal.gallery.ui.theme.OneUiMotion
import com.onegal.gallery.ui.theme.oneUiGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 120Hz Zero-Recomposition MediaGrid:
 * - Google Photos-style Date Group Quick-Select circular monochrome checkmark on every date header
 * - Continuous Drag-to-Select Sweep across rows with smooth top/bottom viewport edge auto-scroll
 * - Two-finger Pinch-to-Zoom grid density across 3 <-> 4 <-> 5 columns with haptic snap ticks
 * - Deterministic keys, contentType slots, original aspect ratio support, and frosted fast-scroll thumb
 */
@Composable
fun MediaGrid(
    sections: List<DateSection>,
    allItemsFlat: List<MediaItem>,
    columns: Int,
    isDarkTheme: Boolean,
    originalAspectRatio: Boolean,
    showFormatBadges: Boolean,
    onColumnsChanged: (Int) -> Unit,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onItemClick: (MediaItem, Int) -> Unit,
    onItemLongClick: (MediaItem) -> Unit,
    onToggleDateSection: (List<MediaItem>) -> Unit = {},
    onApplyDragSelection: (Collection<String>, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnColumnsChanged by rememberUpdatedState(onColumnsChanged)
    val currentOnApplyDragSelection by rememberUpdatedState(onApplyDragSelection)

    val thumbSize = remember(columns) {
        val px = when (columns) {
            3 -> 380
            4 -> 300
            else -> 240
        }
        Size(px, px)
    }

    val flatIndexById = remember(allItemsFlat) {
        allItemsFlat.withIndex().associate { it.value.id to it.index }
    }
    val rowIndexToMonthBadge = remember(sections) {
        val list = ArrayList<String>(allItemsFlat.size + sections.size)
        for (section in sections) {
            list.add(section.monthBadge)
            repeat(section.items.size) { list.add(section.monthBadge) }
        }
        list
    }

    // Continuous drag-to-select edge auto-scroll driver
    var dragAutoScrollPxPerFrame by remember { mutableFloatStateOf(0f) }
    var activeDragPointer by remember { mutableStateOf<Offset?>(null) }
    var dragSelectTargetState by remember { mutableStateOf(true) }
    val dragAnchorIndex = remember { mutableStateOf<Int?>(null) }

    fun findMediaIdAtOffset(pointer: Offset): String? {
        val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            val key = info.key as? String ?: return@firstOrNull false
            if (key.startsWith("hdr_")) return@firstOrNull false
            val left = info.offset.x.toFloat()
            val top = info.offset.y.toFloat()
            val right = left + info.size.width.toFloat()
            val bottom = top + info.size.height.toFloat()
            pointer.x in left..right && pointer.y in top..bottom
        }
        return hit?.key as? String
    }

    fun applySweepToOffset(pointer: Offset) {
        val hitId = findMediaIdAtOffset(pointer) ?: return
        val hitIndex = flatIndexById[hitId] ?: return
        val anchor = dragAnchorIndex.value ?: hitIndex.also { dragAnchorIndex.value = it }
        val start = minOf(anchor, hitIndex).coerceIn(0, allItemsFlat.lastIndex)
        val end = maxOf(anchor, hitIndex).coerceIn(0, allItemsFlat.lastIndex)
        if (start <= end) {
            val sweptIds = allItemsFlat.subList(start, end + 1).map { it.id }
            currentOnApplyDragSelection(sweptIds, dragSelectTargetState)
        }
    }

    LaunchedEffect(dragAutoScrollPxPerFrame, isSelectionMode) {
        if (!isSelectionMode || dragAutoScrollPxPerFrame == 0f) return@LaunchedEffect
        while (dragAutoScrollPxPerFrame != 0f) {
            gridState.scrollBy(dragAutoScrollPxPerFrame)
            activeDragPointer?.let { applySweepToOffset(it) }
            delay(16L)
        }
    }

    // Two-finger pinch-to-zoom grid density across 3 <-> 4 <-> 5 columns with haptic tick at each snap
    val pinchModifier = Modifier.pointerInput(Unit) {
        var cumulativeZoom = 1f
        detectTransformGestures(panZoomLock = false) { _, _, zoom, _ ->
            if (zoom != 1f) {
                cumulativeZoom *= zoom
                val cols = currentColumns
                if (cumulativeZoom > 1.22f && cols > 3) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnColumnsChanged((cols - 1).coerceIn(3, 5))
                    cumulativeZoom = 1f
                } else if (cumulativeZoom < 0.82f && cols < 5) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnColumnsChanged((cols + 1).coerceIn(3, 5))
                    cumulativeZoom = 1f
                }
            }
        }
    }

    // Continuous drag-to-select sweep when selection mode is active
    val edgeThresholdPx = with(density) { 72.dp.toPx() }
    val maxAutoScrollPx = with(density) { 20.dp.toPx() }
    val dragSelectModifier = if (isSelectionMode) {
        Modifier.pointerInput(isSelectionMode, allItemsFlat) {
            detectDragGestures(
                onDragStart = { startOffset ->
                    activeDragPointer = startOffset
                    val startId = findMediaIdAtOffset(startOffset)
                    if (startId != null) {
                        val shouldSelect = startId !in currentSelectedIds
                        dragSelectTargetState = shouldSelect
                        dragAnchorIndex.value = flatIndexById[startId]
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnApplyDragSelection(listOf(startId), shouldSelect)
                    } else {
                        dragAnchorIndex.value = null
                    }
                },
                onDrag = { change, _ ->
                    if (dragAnchorIndex.value != null) {
                        change.consume()
                        val pos = change.position
                        activeDragPointer = pos
                        applySweepToOffset(pos)
                        val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
                        dragAutoScrollPxPerFrame = when {
                            pos.y < edgeThresholdPx -> {
                                val ratio = ((edgeThresholdPx - pos.y) / edgeThresholdPx).coerceIn(0f, 1f)
                                -maxAutoScrollPx * ratio
                            }
                            viewportHeight > 0f && pos.y > viewportHeight - edgeThresholdPx -> {
                                val ratio = ((pos.y - (viewportHeight - edgeThresholdPx)) / edgeThresholdPx).coerceIn(0f, 1f)
                                maxAutoScrollPx * ratio
                            }
                            else -> 0f
                        }
                    }
                },
                onDragEnd = {
                    dragAutoScrollPxPerFrame = 0f
                    activeDragPointer = null
                    dragAnchorIndex.value = null
                },
                onDragCancel = {
                    dragAutoScrollPxPerFrame = 0f
                    activeDragPointer = null
                    dragAnchorIndex.value = null
                }
            )
        }
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceIn(3, 5)),
            state = gridState,
            contentPadding = PaddingValues(
                start = 2.dp,
                end = 2.dp,
                top = 4.dp,
                bottom = 112.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxSize()
                .then(pinchModifier)
                .then(dragSelectModifier)
        ) {
            sections.forEach { section ->
                item(
                    key = "hdr_${section.key}",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "date_header"
                ) {
                    val allSectionSelected = section.items.isNotEmpty() &&
                        section.items.all { it.id in selectedIds }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = OneUiMotion.standardSpring(),
                                fadeOutSpec = OneUiMotion.standardSpring(),
                                placementSpec = OneUiMotion.placementSpring
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleDateSection(section.items)
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleDateSection(section.items)
                                }
                            )
                            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "· ${section.items.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))

                        // Google Photos-style Date Group Quick-Select monochrome checkmark toggle
                        AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = fadeIn(OneUiMotion.standardSpring()),
                            exit = fadeOut(OneUiMotion.standardSpring())
                        ) {
                            val checkFill = if (allSectionSelected) {
                                if (isDarkTheme) Color.White else OneUiColors.LightPrimaryAccent
                            } else {
                                Color.Transparent
                            }
                            val checkTint = if (isDarkTheme) Color.Black else Color.White
                            val borderColor = if (allSectionSelected) {
                                checkFill
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(checkFill)
                                    .border(1.5.dp, borderColor, CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleDateSection(section.items)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (allSectionSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Select all from ${section.title}",
                                        tint = checkTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = section.items,
                    key = { _, item -> item.id },
                    contentType = { _, item -> item.type }
                ) { _, item ->
                    val flatIndex = flatIndexById[item.id] ?: 0
                    val isSelected = item.id in selectedIds
                    val imageRequest = remember(item.uri, thumbSize) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size(thumbSize)
                            .crossfade(100)
                            .build()
                    }
                    MediaThumbnailTile(
                        item = item,
                        imageRequest = imageRequest,
                        columns = columns,
                        isDarkTheme = isDarkTheme,
                        originalAspectRatio = originalAspectRatio,
                        showFormatBadges = showFormatBadges,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = { onItemClick(item, flatIndex) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onItemLongClick(item)
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = OneUiMotion.standardSpring(),
                            fadeOutSpec = OneUiMotion.standardSpring(),
                            placementSpec = OneUiMotion.placementSpring
                        )
                    )
                }
            }
        }

        if (rowIndexToMonthBadge.size > 16) {
            FastScrollDateThumb(
                gridState = gridState,
                isDarkTheme = isDarkTheme,
                totalItems = rowIndexToMonthBadge.size,
                dateBadgeForIndex = { idx ->
                    rowIndexToMonthBadge.getOrElse(idx.coerceIn(0, rowIndexToMonthBadge.lastIndex)) { "" }
                },
                onScrollToItem = { target ->
                    scope.launch {
                        gridState.scrollToItem(target.coerceIn(0, rowIndexToMonthBadge.lastIndex))
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 18.dp, bottom = 104.dp)
            )
        }
    }
}

@Composable
private fun MediaThumbnailTile(
    item: MediaItem,
    imageRequest: ImageRequest,
    columns: Int,
    isDarkTheme: Boolean,
    originalAspectRatio: Boolean,
    showFormatBadges: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tileRatio = if (originalAspectRatio) item.aspectRatio else 1f
    val badge = if (showFormatBadges) item.formatBadge else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(tileRatio)
            .clip(RoundedCornerShape(if (columns <= 3) 6.dp else 4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = item.displayName,
            contentScale = if (originalAspectRatio) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSelected) 6.dp else 0.dp)
                .clip(RoundedCornerShape(if (isSelected) 12.dp else 0.dp))
        )

        if (badge != null && !isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCC121212))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
        }

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                if (item.durationMs > 0L) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = formatDurationMs(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        if (item.isFavorite && !isSelectionMode) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(14.dp)
            )
        }

        // Pure monochrome crisp white selection checkmarks in Dark Mode
        if (isSelectionMode) {
            val checkFill = if (isSelected) {
                if (isDarkTheme) Color.White else OneUiColors.LightPrimaryAccent
            } else {
                Color.Black.copy(alpha = 0.45f)
            }
            val checkIconTint = if (isDarkTheme) Color.Black else Color.White
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(checkFill)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) checkFill else Color.White.copy(alpha = 0.85f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = checkIconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FastScrollDateThumb(
    gridState: LazyGridState,
    isDarkTheme: Boolean,
    totalItems: Int,
    dateBadgeForIndex: (Int) -> String,
    onScrollToItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val scrollFraction by remember(totalItems) {
        derivedStateOf {
            if (totalItems <= 1) 0f
            else (gridState.firstVisibleItemIndex.toFloat() / (totalItems - 1).toFloat()).coerceIn(0f, 1f)
        }
    }
    val activeFraction = if (isDragging) dragFraction else scrollFraction
    val activeIndex = (activeFraction * (totalItems - 1)).roundToInt().coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    val activeBadge = dateBadgeForIndex(activeIndex)
    val showThumb by remember(gridState, isDragging) {
        derivedStateOf { isDragging || gridState.isScrollInProgress }
    }

    LaunchedEffect(activeBadge, isDragging) {
        if (isDragging && activeBadge.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BoxWithConstraints(modifier = modifier.width(136.dp)) {
        val density = LocalDensity.current
        val trackHeightPx = with(density) { maxHeight.toPx() - 48.dp.toPx() }.coerceAtLeast(1f)
        val thumbOffsetY = (activeFraction * trackHeightPx).roundToInt()

        AnimatedVisibility(
            visible = showThumb,
            enter = fadeIn(OneUiMotion.standardSpring()),
            exit = fadeOut(OneUiMotion.standardSpring()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetY) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (isDragging && activeBadge.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .oneUiGlass(isDarkTheme = isDarkTheme, cornerRadius = 18.dp, alpha = 0.80f)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = activeBadge,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 30.dp, height = 46.dp)
                        .then(
                            if (isDragging) {
                                Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier.oneUiGlass(isDarkTheme = isDarkTheme, cornerRadius = 22.dp, alpha = 0.80f)
                            }
                        )
                        .pointerInput(totalItems, trackHeightPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragFraction = scrollFraction
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragFraction = (dragFraction + dragAmount / trackHeightPx).coerceIn(0f, 1f)
                                    val target = (dragFraction * (totalItems - 1)).roundToInt()
                                    onScrollToItem(target)
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = "Fast scroll",
                        tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
