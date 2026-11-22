package com.onegal.gallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onegal.gallery.data.model.SpecialCollection
import com.onegal.gallery.ui.theme.oneUiGlass

/**
 * Samsung One UI 8.0 Floating Menu Sheet (matching uploaded screenshot 3):
 * - Top row: 4 circular monochrome buttons (Videos, Favorites, Recent, External)
 * - Middle: 2x2 rounded capsule buttons (Create album, Hide albums, Recycle bin, Settings)
 * - Zero cloud/AI/Studio bloat — 100% local monochrome glassmorphism.
 */
@Composable
fun OneUiMenuBottomSheet(
    isDarkTheme: Boolean,
    favoritesCount: Int,
    videosCount: Int,
    trashCount: Int,
    externalDrivesCount: Int,
    onSelectCollection: (SpecialCollection) -> Unit,
    onOpenExternal: () -> Unit,
    onCreateAlbum: () -> Unit,
    onOpenHideAlbums: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .oneUiGlass(isDarkTheme = isDarkTheme, cornerRadius = 28.dp, alpha = 0.80f)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            // Top 4 circular quick shortcuts (Videos, Favorites, Recent, External)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CircularMenuAction(
                    icon = Icons.Rounded.Videocam,
                    label = "Videos",
                    badgeText = if (videosCount > 0) "$videosCount" else null,
                    onClick = { onSelectCollection(SpecialCollection.VIDEOS) },
                    modifier = Modifier.weight(1f)
                )
                CircularMenuAction(
                    icon = Icons.Rounded.FavoriteBorder,
                    label = "Favorites",
                    badgeText = if (favoritesCount > 0) "$favoritesCount" else null,
                    onClick = { onSelectCollection(SpecialCollection.FAVORITES) },
                    modifier = Modifier.weight(1f)
                )
                CircularMenuAction(
                    icon = Icons.Rounded.Schedule,
                    label = "Recent",
                    badgeText = null,
                    onClick = { onSelectCollection(SpecialCollection.RECENT) },
                    modifier = Modifier.weight(1f)
                )
                CircularMenuAction(
                    icon = Icons.Rounded.Usb,
                    label = "External",
                    badgeText = if (externalDrivesCount > 0) "$externalDrivesCount" else null,
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // 2x2 Capsule action grid matching One UI 8.0 Menu sheet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuCapsuleButton(
                    icon = Icons.Rounded.CreateNewFolder,
                    label = "Create album",
                    onClick = onCreateAlbum,
                    modifier = Modifier.weight(1f)
                )
                MenuCapsuleButton(
                    icon = Icons.Rounded.VisibilityOff,
                    label = "Hide albums",
                    onClick = onOpenHideAlbums,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuCapsuleButton(
                    icon = Icons.Rounded.DeleteOutline,
                    label = if (trashCount > 0) "Recycle bin ($trashCount)" else "Recycle bin",
                    onClick = { onSelectCollection(SpecialCollection.TRASH) },
                    modifier = Modifier.weight(1f)
                )
                MenuCapsuleButton(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CircularMenuAction(
    icon: ImageVector,
    label: String,
    badgeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MenuCapsuleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
