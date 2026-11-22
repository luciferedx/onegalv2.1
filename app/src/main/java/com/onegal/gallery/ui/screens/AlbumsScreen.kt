package com.onegal.gallery.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.onegal.gallery.data.model.AlbumItem
import com.onegal.gallery.ui.theme.OneUiMotion

/**
 * Samsung One UI 8.0 Albums Grid (v2):
 * - Matches reference screenshot: bold "All albums" section title, 3-column squircle cards
 * - Displays persistent 0-item empty albums with clean placeholder thumbnail & "0" badge
 * - Spring placement animations across 2 <-> 3 <-> 4 columns
 */
@Composable
fun AlbumsScreen(
    albums: List<AlbumItem>,
    columns: Int,
    onColumnsChanged: (Int) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val pinchModifier = Modifier.pointerInput(columns) {
        var cumulativeZoom = 1f
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            cumulativeZoom = 1f
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) {
                        cumulativeZoom *= zoom
                        if (cumulativeZoom > 1.22f && columns > 2) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onColumnsChanged((columns - 1).coerceIn(2, 4))
                            cumulativeZoom = 1f
                        } else if (cumulativeZoom < 0.82f && columns < 4) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onColumnsChanged((columns + 1).coerceIn(2, 4))
                            cumulativeZoom = 1f
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxSize()
            .then(pinchModifier)
    ) {
        item(
            key = "all_albums_heading",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "section_heading"
        ) {
            Text(
                text = "All albums",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        items(
            items = albums,
            key = { "${it.source}_${it.id}" },
            contentType = { if (it.coverItem == null) "empty_album" else "media_album" }
        ) { album ->
            OneUiAlbumCard(
                album = album,
                onClick = { onAlbumClick(album) },
                modifier = Modifier.animateItem(
                    fadeInSpec = OneUiMotion.standardSpring(),
                    fadeOutSpec = OneUiMotion.standardSpring(),
                    placementSpec = OneUiMotion.placementSpring
                )
            )
        }
    }
}

@Composable
fun OneUiAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coverUri = album.coverItem?.uri
    val request = remember(coverUri) {
        coverUri?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(Size(480, 480))
                .crossfade(110)
                .build()
        }
    }
    val cardShape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline), cardShape),
            contentAlignment = Alignment.Center
        ) {
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Clean placeholder for persistent 0-item user-created empty albums
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = "${album.name} empty album",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )
            }

            if (album.name.equals("Camera", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoCamera,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            if (album.isMerged) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MergeType,
                        contentDescription = "Merged album",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Text(
            text = "${album.itemCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
