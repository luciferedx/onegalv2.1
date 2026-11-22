package com.onegal.gallery.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onegal.gallery.data.model.ThemePreference

/**
 * Onegal Samsung One UI 8.0 Monochrome AMOLED & Frosted-Glass Palette.
 * Pure pitch-black #000000 canvas, #161618 grouped settings cards, Color(0xCC121212) frosted-glass
 * pills/sheets with 1.dp Color(0x26FFFFFF) hairline borders, and crisp #FFFFFF monochrome accents.
 */
object OneUiColors {
    val AmoledBlack = Color(0xFF000000)
    val DarkSettingsCard = Color(0xFF161618)
    val DarkGlassBase = Color(0xFF121212)
    val DarkGlassSurface = Color(0xCC121212)
    val DarkControlPill = Color(0xFF262629)
    val DarkSwitchTrackOff = Color(0xFF3A3A3D)
    val DarkSpecularBorder = Color(0x26FFFFFF) // 15% white hairline for high contrast on white & black media
    val DarkHairline = Color(0x14FFFFFF)
    val DarkPrimaryAccent = Color(0xFFFFFFFF)
    val DarkOnPrimaryAccent = Color(0xFF000000)
    val DarkSecondaryText = Color(0xFF8E8E93)
    val DarkTertiaryText = Color(0xFFA1A1AA)

    val LightCanvas = Color(0xFFF6F6F8)
    val LightCard = Color(0xFFFFFFFF)
    val LightControlPill = Color(0xFFE9E9EE)
    val LightBorder = Color(0xFFE5E5EA)
    val LightPrimaryAccent = Color(0xFF111114)
    val LightOnPrimaryAccent = Color(0xFFFFFFFF)
    val LightSecondaryText = Color(0xFF63636A)

    val Destructive = Color(0xFFFF453A)
}

/**
 * Critically damped Samsung One UI 8.0 120Hz spring physics constants.
 */
object OneUiMotion {
    const val DAMPING_RATIO = 0.85f
    const val STIFFNESS = Spring.StiffnessMediumLow

    fun <T> standardSpring(): SpringSpec<T> = spring(
        dampingRatio = DAMPING_RATIO,
        stiffness = STIFFNESS
    )

    fun <T> rubberBandSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val placementSpring: SpringSpec<IntOffset> = spring(
        dampingRatio = DAMPING_RATIO,
        stiffness = STIFFNESS
    )
}

fun Modifier.oneUiGlass(
    isDarkTheme: Boolean,
    cornerRadius: Dp = 28.dp,
    alpha: Float = 0.80f
): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val bg = if (isDarkTheme) {
        if (alpha == 0.80f) OneUiColors.DarkGlassSurface else OneUiColors.DarkGlassBase.copy(alpha = alpha)
    } else {
        Color.White.copy(alpha = 0.90f)
    }
    val border = if (isDarkTheme) {
        OneUiColors.DarkSpecularBorder
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    this
        .clip(shape)
        .background(bg)
        .border(BorderStroke(1.dp, border), shape)
}

private val OneUiDarkMonochromeScheme = darkColorScheme(
    primary = OneUiColors.DarkPrimaryAccent,
    onPrimary = OneUiColors.DarkOnPrimaryAccent,
    secondary = Color(0xFFE5E5EA),
    onSecondary = Color(0xFF000000),
    background = OneUiColors.AmoledBlack,
    onBackground = Color(0xFFFFFFFF),
    surface = OneUiColors.AmoledBlack,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = OneUiColors.DarkSettingsCard,
    onSurfaceVariant = OneUiColors.DarkSecondaryText,
    surfaceContainer = OneUiColors.DarkSettingsCard,
    surfaceContainerHigh = OneUiColors.DarkGlassSurface,
    surfaceContainerHighest = OneUiColors.DarkControlPill,
    outline = OneUiColors.DarkSpecularBorder,
    outlineVariant = OneUiColors.DarkHairline,
    error = OneUiColors.Destructive
)

private val OneUiLightScheme = lightColorScheme(
    primary = OneUiColors.LightPrimaryAccent,
    onPrimary = OneUiColors.LightOnPrimaryAccent,
    secondary = Color(0xFF3A3A3D),
    onSecondary = Color.White,
    background = OneUiColors.LightCanvas,
    onBackground = Color(0xFF0A0A0C),
    surface = OneUiColors.LightCanvas,
    onSurface = Color(0xFF0A0A0C),
    surfaceVariant = OneUiColors.LightCard,
    onSurfaceVariant = OneUiColors.LightSecondaryText,
    surfaceContainer = Color(0xFFEEEEF2),
    surfaceContainerHigh = OneUiColors.LightCard,
    surfaceContainerHighest = OneUiColors.LightControlPill,
    outline = OneUiColors.LightBorder,
    outlineVariant = Color(0xFFEFEFF4),
    error = OneUiColors.Destructive
)

private val OneUiTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val OneUiShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OnegalTheme(
    preference: ThemePreference = ThemePreference.DARK_AMOLED,
    content: @Composable () -> Unit
) {
    val useDark = when (preference) {
        ThemePreference.DARK_AMOLED -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) OneUiDarkMonochromeScheme else OneUiLightScheme,
        typography = OneUiTypography,
        shapes = OneUiShapes,
        content = content
    )
}
