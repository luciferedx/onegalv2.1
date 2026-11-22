package com.onegal.gallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onegal.gallery.data.model.SmartMediaFilter
import com.onegal.gallery.data.model.SortOrder
import com.onegal.gallery.ui.theme.OneUiMotion
import com.onegal.gallery.ui.theme.oneUiGlass

class OneUiCollapseState(
    val maxHeightPx: Float,
    val minHeightPx: Float
) {
    var heightOffsetPx by mutableFloatStateOf(0f)

    val collapseRangePx: Float
        get() = (maxHeightPx - minHeightPx).coerceAtLeast(1f)

    val collapsedFraction: Float
        get() = (-heightOffsetPx / collapseRangePx).coerceIn(0f, 1f)

    val currentHeightPx: Float
        get() = (maxHeightPx + heightOffsetPx).coerceIn(minHeightPx, maxHeightPx)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (delta < 0f && heightOffsetPx > -collapseRangePx) {
                val previous = heightOffsetPx
                heightOffsetPx = (heightOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
                return Offset(0f, heightOffsetPx - previous)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val delta = available.y
            if (delta > 0f && heightOffsetPx < 0f) {
                val previous = heightOffsetPx
                heightOffsetPx = (heightOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
                return Offset(0f, heightOffsetPx - previous)
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberOneUiCollapseState(
    expandedHeight: Dp = 228.dp,
    collapsedHeight: Dp = 56.dp
): OneUiCollapseState {
    val density = LocalDensity.current
    val maxPx = with(density) { expandedHeight.toPx() }
    val minPx = with(density) { collapsedHeight.toPx() }
    return remember(maxPx, minPx) { OneUiCollapseState(maxPx, minPx) }
}

/**
 * Samsung One UI 8.0 Collapsible Reachability Header (v2):
 * - Zero-recomposition draw-phase alpha & translation via graphicsLayer + derivedStateOf
 * - Pure monochrome white iconography and typography in Dark Mode
 * - Prominent '+' Create Album button on the Albums tab matching One UI 8.0 reference
 */
@Composable
fun OneUiCollapsibleHeader(
    title: String,
    subtitle: String,
    collapseState: OneUiCollapseState,
    showBackButton: Boolean,
    showCreateAlbumAction: Boolean,
    onCreateAlbumClick: () -> Unit,
    onBackClick: () -> Unit,
    isSelectionMode: Boolean,
    selectedCount: Int,
    totalSelectableCount: Int,
    onToggleSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    currentColumns: Int,
    onCycleColumns: () -> Unit,
    currentSort: SortOrder,
    onSortSelected: (SortOrder) -> Unit,
    onEnterSelection: () -> Unit,
    onOpenSettings: () -> Unit,
    isDarkTheme: Boolean = true,
    showFilterChips: Boolean = false,
    activeFilter: SmartMediaFilter = SmartMediaFilter.ALL,
    onFilterSelected: (SmartMediaFilter) -> Unit = {}
) {
    val density = LocalDensity.current
    val currentHeightDp by remember(collapseState) {
        derivedStateOf { with(density) { collapseState.currentHeightPx.toDp() } }
    }
    val showCompactTitle by remember(collapseState, showBackButton) {
        derivedStateOf { collapseState.collapsedFraction > 0.55f || showBackButton }
    }
    var moreExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleSelectAll) {
                    val allSelected = totalSelectableCount > 0 && selectedCount == totalSelectableCount
                    Icon(
                        imageVector = if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = "Select all",
                        tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = if (selectedCount == 0) "Select items" else "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancelSelection) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isSearchActive) 60.dp else currentHeightDp)
        ) {
            // Expanded reachability header centered in upper third — draw-phase opacity/translation
            if (!isSearchActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .graphicsLayer {
                            val fraction = collapseState.collapsedFraction
                            alpha = (1f - fraction * 1.48f).coerceIn(0f, 1f)
                            translationY = -fraction * 28f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Compact sticky top bar anchored at bottom of collapsing box
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                if (isSearchActive) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search filename, folder, or type",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onSearchToggle(false) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = showCompactTitle,
                        enter = fadeIn(OneUiMotion.standardSpring()),
                        exit = fadeOut(OneUiMotion.standardSpring()),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!showCompactTitle) {
                        Spacer(Modifier.weight(1f))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showCreateAlbumAction) {
                            IconButton(onClick = onCreateAlbumClick) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Create new album",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        IconButton(onClick = { onSearchToggle(true) }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        if (!showCreateAlbumAction) {
                            IconButton(onClick = onEnterSelection) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = "Select",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = onCycleColumns) {
                                Icon(
                                    imageVector = Icons.Rounded.GridView,
                                    contentDescription = "View density ($currentColumns columns)",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { moreExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(22.dp)
                            ) {
                                if (showCreateAlbumAction) {
                                    DropdownMenuItem(
                                        text = { Text("Create album", color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            moreExpanded = false
                                            onCreateAlbumClick()
                                        }
                                    )
                                }
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (order == currentSort) "${order.label}  •" else order.label,
                                                fontWeight = if (order == currentSort) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (order == currentSort) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        },
                                        onClick = {
                                            onSortSelected(order)
                                            moreExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Settings", color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        moreExpanded = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Minimalist monochrome frosted-glass smart media type filter pills (All, Videos, Screenshots, Favorites)
        if (showFilterChips && !isSearchActive) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SmartMediaFilter.entries, key = { it.name }) { filter ->
                    val selected = filter == activeFilter
                    val pillModifier = if (selected) {
                        Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onFilterSelected(filter) }
                    } else {
                        Modifier
                            .oneUiGlass(isDarkTheme = isDarkTheme, cornerRadius = 18.dp, alpha = 0.80f)
                            .clickable { onFilterSelected(filter) }
                    }
                    Box(
                        modifier = pillModifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                            },
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
