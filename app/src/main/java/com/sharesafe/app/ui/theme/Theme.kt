package com.sharesafe.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sharesafe.app.data.ThemeMode

private fun lightScheme(palette: ThemePalette) = lightColorScheme().let { base ->
    val accent = palette.lightColors()
    base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        tertiaryContainer = accent.tertiary.copy(alpha = 0.18f),
        background = SurfaceLight,
        onBackground = OnSurfaceLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceContainerLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        surfaceContainer = SurfaceContainerLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        surfaceContainerHighest = SurfaceContainerHighLight,
        outline = OutlineLight,
        error = ErrorLight,
        errorContainer = ErrorContainerLight,
    )
}

private fun darkScheme(palette: ThemePalette) = darkColorScheme().let { base ->
    val accent = palette.darkColors()
    base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        onSecondary = accent.onSecondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
        tertiary = accent.tertiary,
        tertiaryContainer = accent.tertiary.copy(alpha = 0.22f),
        background = SurfaceDark,
        onBackground = OnSurfaceDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceContainerDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        surfaceContainer = SurfaceContainerDark,
        surfaceContainerHigh = SurfaceContainerHighDark,
        surfaceContainerHighest = SurfaceContainerHighDark,
        outline = OutlineDark,
        error = ErrorDark,
        errorContainer = ErrorContainerDark,
    )
}

val ShareSafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun ShareSafeTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = false,
    palette: ThemePalette = ThemePalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    // Material You is opt-in: the chosen palette stays the default, wallpaper colours are a choice.
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkScheme(palette)
        else -> lightScheme(palette)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ShareSafeTypography,
        shapes = ShareSafeShapes,
        content = content,
    )
}

/** Small helper so a composable can react to the user turning motion off in Settings. */
@Composable
fun motionDuration(millis: Int): Int = if (LocalAnimationsEnabled.current) millis else 0

/** Composition local carrying the user's motion preference down to every animated component. */
val LocalAnimationsEnabled = androidx.compose.runtime.compositionLocalOf { true }
