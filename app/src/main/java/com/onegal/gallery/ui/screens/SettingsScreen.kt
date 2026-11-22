package com.onegal.gallery.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onegal.gallery.data.model.GallerySettings
import com.onegal.gallery.data.model.ThemePreference
import com.onegal.gallery.ui.theme.OneUiColors

/**
 * Full Samsung One UI 8.0 Gallery Settings Screen (Version 2):
 * - Pitch-black AMOLED #000000 canvas, #161618 grouped cards (26.dp radius), 0.75.dp specular borders
 * - Pure monochrome white switches & active highlights (zero blue accents in Dark Mode)
 * - 100% local settings only: View & Display, Albums & Organization, Privacy/Details/Storage, About Onegal
 * - Strictly excludes cloud sync, OneDrive, and AI editing features.
 */
@Composable
fun SettingsScreen(
    settings: GallerySettings,
    isDarkTheme: Boolean,
    hasMediaPermission: Boolean,
    trashedCount: Int,
    hiddenAlbumCount: Int,
    essentialAlbumCount: Int,
    thumbnailCacheBytes: Long,
    onBack: () -> Unit,
    onUpdateSettings: ((GallerySettings) -> GallerySettings) -> Unit,
    onOpenEssentialAlbumsPicker: () -> Unit,
    onOpenHideAlbumsPicker: () -> Unit,
    onEmptyTrash: () -> Unit,
    onClearThumbnailCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // One UI 8.0 Settings Top App Bar ("< Gallery settings")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Gallery settings",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section 1: View & Display
            item(key = "sec_view_header") {
                SettingsSectionHeader("View & Display")
            }
            item(key = "sec_view_card") {
                OneUiSettingsCard(isDarkTheme = isDarkTheme) {
                    SettingsSwitchRow(
                        title = "Full screen scrolling",
                        subtitle = "Extend photos and videos edge-to-edge behind status and navigation bars in the full-screen viewer.",
                        checked = settings.fullScreenScrolling,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(fullScreenScrolling = checked) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Auto play motion photos & videos",
                        subtitle = "Automatically play videos and motion captures silently while browsing.",
                        checked = settings.autoPlayVideos,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(autoPlayVideos = checked) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Original aspect ratio grid",
                        subtitle = if (settings.originalAspectRatioGrid) {
                            "Showing uncropped photo proportions in the Pictures grid."
                        } else {
                            "Showing uniform square-cropped thumbnails in the Pictures grid."
                        },
                        checked = settings.originalAspectRatioGrid,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(originalAspectRatioGrid = checked) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Show file format badges",
                        subtitle = "Display RAW, 8K, 4K, and GIF format indicators on grid thumbnails.",
                        checked = settings.showFormatBadges,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showFormatBadges = checked) }
                        }
                    )
                }
            }

            // Section 2: Albums & Organization
            item(key = "sec_albums_header") {
                SettingsSectionHeader("Albums & Organization")
            }
            item(key = "sec_albums_card") {
                OneUiSettingsCard(isDarkTheme = isDarkTheme) {
                    SettingsSwitchRow(
                        title = "Select essential albums",
                        subtitle = if (settings.essentialAlbumsEnabled) {
                            "$essentialAlbumCount essential album(s) pinned to Albums tab. Tap to choose."
                        } else {
                            "Show only the albums you select on the Albums tab instead of showing them all."
                        },
                        checked = settings.essentialAlbumsEnabled,
                        isDarkTheme = isDarkTheme,
                        onRowClick = onOpenEssentialAlbumsPicker,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(essentialAlbumsEnabled = checked) }
                            if (checked && essentialAlbumCount == 0) {
                                onOpenEssentialAlbumsPicker()
                            }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Merge albums",
                        subtitle = "Albums with the same name across Internal Storage and External USB/NVMe drives will be shown as a single album.",
                        checked = settings.mergeAlbums,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(mergeAlbums = checked) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsActionRow(
                        title = "Hide albums",
                        subtitle = if (hiddenAlbumCount > 0) {
                            "$hiddenAlbumCount album(s) currently hidden without deleting files"
                        } else {
                            "Hide specific folders from Pictures and Albums without deleting files"
                        },
                        onClick = onOpenHideAlbumsPicker
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Show empty albums",
                        subtitle = "Keep user-created 0-item folders visible in the Albums tab and Copy/Move picker.",
                        checked = settings.showEmptyAlbums,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showEmptyAlbums = checked) }
                        }
                    )
                }
            }

            // Section 3: Privacy, Details & Storage
            item(key = "sec_privacy_header") {
                SettingsSectionHeader("Privacy, Details & Storage")
            }
            item(key = "sec_privacy_card") {
                OneUiSettingsCard(isDarkTheme = isDarkTheme) {
                    SettingsSwitchRow(
                        title = "Show full location & EXIF in details",
                        subtitle = "Include camera lens metadata, shutter exposure, and GPS coordinates in the details sheet.",
                        checked = settings.showFullExifLocation,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showFullExifLocation = checked) }
                        }
                    )
                    SettingsCardDivider()
                    SettingsSwitchRow(
                        title = "Recycle bin (30-day retention)",
                        subtitle = "Keep deleted photos and videos in the local Recycle bin for 30 days before permanent removal.",
                        checked = settings.trashRetention30Days,
                        isDarkTheme = isDarkTheme,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(trashRetention30Days = checked) }
                        }
                    )
                    SettingsCardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = trashedCount > 0, onClick = onEmptyTrash)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Empty Recycle bin",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (trashedCount > 0) OneUiColors.Destructive else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (trashedCount > 0) {
                                    "$trashedCount local item(s) waiting for permanent deletion"
                                } else {
                                    "Recycle bin is empty"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = "Empty Recycle bin",
                            tint = if (trashedCount > 0) OneUiColors.Destructive else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Section 4: Theme Mode
            item(key = "sec_theme_header") {
                SettingsSectionHeader("Theme Mode")
            }
            item(key = "sec_theme_card") {
                OneUiSettingsCard(isDarkTheme = isDarkTheme) {
                    val options = listOf(
                        Triple(ThemePreference.DARK_AMOLED, "Pitch-Black AMOLED Dark", "Pure #000000 canvas with monochrome white accents"),
                        Triple(ThemePreference.LIGHT, "One UI Light", "Soft #F6F6F8 canvas with crisp high-contrast ink"),
                        Triple(ThemePreference.SYSTEM, "System Default", "Follow Android system Dark/Light mode")
                    )
                    options.forEachIndexed { idx, (pref, label, desc) ->
                        val selected = settings.themePreference == pref
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUpdateSettings { it.copy(themePreference = pref) } }
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        if (idx < options.lastIndex) SettingsCardDivider()
                    }
                }
            }

            // Section 5: About Onegal & Cache
            item(key = "sec_about_header") {
                SettingsSectionHeader("About Onegal")
            }
            item(key = "sec_about_card") {
                OneUiSettingsCard(isDarkTheme = isDarkTheme) {
                    SettingsInfoRow(
                        title = "Version",
                        value = "Onegal 2.0.0 · 100% Local · Zero AI · Zero Cloud"
                    )
                    SettingsCardDivider()
                    SettingsInfoRow(
                        title = "Permissions",
                        value = if (hasMediaPermission) {
                            "Local Photos, Videos & EXIF Location granted"
                        } else {
                            "Media permission not granted"
                        }
                    )
                    SettingsCardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearThumbnailCache)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clear Thumbnail Cache",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Local Coil 3 disk cache: ${formatBytes(thumbnailCacheBytes)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Rounded.CleaningServices,
                            contentDescription = "Clear thumbnail cache",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun OneUiSettingsCard(
    isDarkTheme: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = if (isDarkTheme) OneUiColors.DarkSettingsCard else OneUiColors.LightCard,
        border = BorderStroke(
            0.75.dp,
            if (isDarkTheme) OneUiColors.DarkSpecularBorder else OneUiColors.LightBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        thickness = 0.75.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDarkTheme: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRowClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRowClick?.invoke() ?: onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(14.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isDarkTheme) Color.Black else Color.White,
                checkedTrackColor = if (isDarkTheme) Color.White else OneUiColors.LightPrimaryAccent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (isDarkTheme) Color(0xFFB4B4BA) else Color.White,
                uncheckedTrackColor = if (isDarkTheme) OneUiColors.DarkSwitchTrackOff else Color(0xFFD1D1D6),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
