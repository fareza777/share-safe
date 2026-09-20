package com.sharesafe.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour themes the user can pick. Each one only swaps the accent roles (primary / secondary /
 * tertiary and their containers); the neutral surfaces stay shared so contrast in both light and
 * dark mode is tuned once and the choice stays a choice and not a risk.
 */
enum class ThemePalette(val id: String) {
    VIOLET("violet"),
    OCEAN("ocean"),
    SUNSET("sunset"),
    FOREST("forest"),
    MONO("mono");

    companion object {
        val DEFAULT = VIOLET
        fun fromId(id: String?): ThemePalette = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The accent roles one palette defines, for one mode. */
internal data class PaletteColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
)

internal fun ThemePalette.lightColors(): PaletteColors = when (this) {
    ThemePalette.VIOLET -> PaletteColors(
        primary = VioletPrimary,
        onPrimary = Color.White,
        primaryContainer = VioletContainer,
        onPrimaryContainer = Color(0xFF1B1340),
        secondary = MintSecondary,
        onSecondary = Color.White,
        secondaryContainer = MintContainer,
        onSecondaryContainer = Color(0xFF06322A),
        tertiary = CoralTertiary,
    )

    ThemePalette.OCEAN -> PaletteColors(
        primary = Color(0xFF1565C0),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E6FF),
        onPrimaryContainer = Color(0xFF04203F),
        secondary = Color(0xFF00838F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFCFF3F8),
        onSecondaryContainer = Color(0xFF00282E),
        tertiary = Color(0xFF5C6BC0),
    )

    ThemePalette.SUNSET -> PaletteColors(
        primary = Color(0xFFE64A19),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCF),
        onPrimaryContainer = Color(0xFF3A1102),
        secondary = Color(0xFFC2185B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD9E2),
        onSecondaryContainer = Color(0xFF3E0018),
        tertiary = Color(0xFFF9A825),
    )

    ThemePalette.FOREST -> PaletteColors(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF04210A),
        secondary = Color(0xFF00695C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFC2EDE7),
        onSecondaryContainer = Color(0xFF00251F),
        tertiary = Color(0xFF827717),
    )

    ThemePalette.MONO -> PaletteColors(
        primary = Color(0xFF2B2B33),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4EC),
        onPrimaryContainer = Color(0xFF14141A),
        secondary = Color(0xFF4A5064),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E5F0),
        onSecondaryContainer = Color(0xFF141824),
        tertiary = Color(0xFF6B7280),
    )
}

internal fun ThemePalette.darkColors(): PaletteColors = when (this) {
    ThemePalette.VIOLET -> PaletteColors(
        primary = VioletPrimaryDark,
        onPrimary = Color(0xFF221A4D),
        primaryContainer = VioletContainerDark,
        onPrimaryContainer = Color(0xFFE7E2FF),
        secondary = MintSecondaryDark,
        onSecondary = Color(0xFF00382C),
        secondaryContainer = MintContainerDark,
        onSecondaryContainer = Color(0xFFD3F7EC),
        tertiary = CoralTertiary,
    )

    ThemePalette.OCEAN -> PaletteColors(
        primary = Color(0xFF9CC7FF),
        onPrimary = Color(0xFF00305F),
        primaryContainer = Color(0xFF12457F),
        onPrimaryContainer = Color(0xFFD6E6FF),
        secondary = Color(0xFF7FE0EC),
        onSecondary = Color(0xFF00363D),
        secondaryContainer = Color(0xFF0A4F58),
        onSecondaryContainer = Color(0xFFCFF3F8),
        tertiary = Color(0xFFB8C3FF),
    )

    ThemePalette.SUNSET -> PaletteColors(
        primary = Color(0xFFFFB59B),
        onPrimary = Color(0xFF5A1B04),
        primaryContainer = Color(0xFF7F2B0C),
        onPrimaryContainer = Color(0xFFFFDBCF),
        secondary = Color(0xFFFFB1C4),
        onSecondary = Color(0xFF5E1130),
        secondaryContainer = Color(0xFF83214A),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFFFD77A),
    )

    ThemePalette.FOREST -> PaletteColors(
        primary = Color(0xFF9BD69C),
        onPrimary = Color(0xFF063A0D),
        primaryContainer = Color(0xFF1B5E20),
        onPrimaryContainer = Color(0xFFC8E6C9),
        secondary = Color(0xFF7FD9CB),
        onSecondary = Color(0xFF00382F),
        secondaryContainer = Color(0xFF00544A),
        onSecondaryContainer = Color(0xFFC2EDE7),
        tertiary = Color(0xFFD8CE6A),
    )

    ThemePalette.MONO -> PaletteColors(
        primary = Color(0xFFE6E6EF),
        onPrimary = Color(0xFF1A1A22),
        primaryContainer = Color(0xFF35353F),
        onPrimaryContainer = Color(0xFFE4E4EC),
        secondary = Color(0xFFC3C8DA),
        onSecondary = Color(0xFF232733),
        secondaryContainer = Color(0xFF333A4C),
        onSecondaryContainer = Color(0xFFE2E5F0),
        tertiary = Color(0xFFA9B0BF),
    )
}
