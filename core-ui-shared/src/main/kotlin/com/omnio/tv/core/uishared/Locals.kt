package com.omnio.tv.core.uishared

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.omnio.tv.domain.model.AppTheme

/**
 * Extended colors. The TV OmnioTheme wrapper builds one of these from the
 * active [OmnioColorScheme] and exposes it through [LocalOmnioExtendedColors]
 * so screens can read non-Material slots (focus ring, rating gold, text
 * tertiary, …).
 */
data class OmnioExtendedColors(
    val backgroundElevated: Color,
    val backgroundCard: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val focusRing: Color,
    val focusBackground: Color,
    val rating: Color
)

val LocalOmnioColors = staticCompositionLocalOf {
    OmnioColorScheme(ThemeColors.Cinematic)
}

val LocalOmnioExtendedColors = staticCompositionLocalOf {
    OmnioExtendedColors(
        backgroundElevated = ThemeColors.Cinematic.backgroundElevated,
        backgroundCard = ThemeColors.Cinematic.backgroundCard,
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF808080),
        focusRing = ThemeColors.Cinematic.focusRing,
        focusBackground = ThemeColors.Cinematic.focusBackground,
        rating = Color(0xFFFFD700)
    )
}

val LocalAppTheme = staticCompositionLocalOf { AppTheme.CINEMATIC }
