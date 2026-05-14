package com.omnio.tv.core.uishared

import androidx.compose.ui.graphics.Color
import com.omnio.tv.domain.model.AppTheme

/**
 * Color palette for each theme.
 * Includes both accent colors and background tints for full theme customization.
 */
data class ThemeColorPalette(
    val secondary: Color,
    val secondaryVariant: Color,
    val onSecondary: Color = Color.White,
    val onSecondaryVariant: Color = Color.White,
    val focusRing: Color,
    val focusBackground: Color,
    // Background colors with subtle theme tinting
    val background: Color = Color(0xFF0D0D0D),
    val backgroundElevated: Color = Color(0xFF1A1A1A),
    val backgroundCard: Color = Color(0xFF242424)
)

object ThemeColors {

    /**
     * Cinematic — true-black, Netflix-red palette. Default theme.
     * bg-0 #000, bg-1 #0A0A0A, bg-2 #141414, bg-3 #1F1F1F, bg-4 #2A2A2A.
     * red #E50914 + red-bright #FF1F2D + red-soft #FF4D6D.
     */
    val Cinematic = ThemeColorPalette(
        secondary = Color(0xFFE50914),
        secondaryVariant = Color(0xFFB0060F),
        focusRing = Color(0xFFFFFFFF),
        focusBackground = Color(0x1FE50914),  // red @ 12% wash
        background = Color(0xFF000000),       // bg-0
        backgroundElevated = Color(0xFF141414), // bg-2
        backgroundCard = Color(0xFF1F1F1F)    // bg-3
    )

    val Crimson = ThemeColorPalette(
        secondary = Color(0xFFE53935),
        secondaryVariant = Color(0xFFC62828),
        focusRing = Color(0xFFFF5252),
        focusBackground = Color(0xFF3D1A1A),
        background = Color(0xFF0D0D0D),
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF241A1A)  // Warm red tint
    )

    val Ocean = ThemeColorPalette(
        secondary = Color(0xFF1E88E5),
        secondaryVariant = Color(0xFF1565C0),
        focusRing = Color(0xFF42A5F5),
        focusBackground = Color(0xFF1A2D3D),
        background = Color(0xFF0D0D0F),      // Cool blue tint
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1A1F24)
    )

    val Violet = ThemeColorPalette(
        secondary = Color(0xFF8E24AA),
        secondaryVariant = Color(0xFF6A1B9A),
        focusRing = Color(0xFFAB47BC),
        focusBackground = Color(0xFF2D1A3D),
        background = Color(0xFF0D0D0F),      // Purple tint
        backgroundElevated = Color(0xFF1A1A1E),
        backgroundCard = Color(0xFF1F1A24)
    )

    val Emerald = ThemeColorPalette(
        secondary = Color(0xFF43A047),
        secondaryVariant = Color(0xFF2E7D32),
        focusRing = Color(0xFF66BB6A),
        focusBackground = Color(0xFF1A3D1E),
        background = Color(0xFF0D0D0D),
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF1A241A)  // Green tint
    )

    val Amber = ThemeColorPalette(
        secondary = Color(0xFFFB8C00),
        secondaryVariant = Color(0xFFEF6C00),
        focusRing = Color(0xFFFFA726),
        focusBackground = Color(0xFF3D2D1A),
        background = Color(0xFF0F0D0D),      // Warm amber tint
        backgroundElevated = Color(0xFF1E1A1A),
        backgroundCard = Color(0xFF24201A)
    )

    val Rose = ThemeColorPalette(
        secondary = Color(0xFFD81B60),
        secondaryVariant = Color(0xFFC2185B),
        focusRing = Color(0xFFEC407A),
        focusBackground = Color(0xFF3D1A2D),
        background = Color(0xFF0D0D0D),
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF241A1F)  // Pink tint
    )

    val White = ThemeColorPalette(
        secondary = Color(0xFFF5F5F5),
        secondaryVariant = Color(0xFFE0E0E0),
        onSecondary = Color(0xFF111111),
        onSecondaryVariant = Color(0xFF111111),
        focusRing = Color(0xFFFFFFFF),
        focusBackground = Color(0xFF303030),
        background = Color(0xFF0D0D0D),
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF222222)
    )

    fun getColorPalette(theme: AppTheme): ThemeColorPalette {
        return when (theme) {
            AppTheme.CINEMATIC -> Cinematic
            AppTheme.CRIMSON -> Crimson
            AppTheme.OCEAN -> Ocean
            AppTheme.VIOLET -> Violet
            AppTheme.EMERALD -> Emerald
            AppTheme.AMBER -> Amber
            AppTheme.ROSE -> Rose
            AppTheme.WHITE -> White
        }
    }
}

/**
 * Per-source accent colors for addon/source identification (RD violet, JF blue, …).
 * Theme-independent — these are brand colors of external providers.
 */
object OmnioSourceColors {
    val Rd = Color(0xFF6C5CE7)      // Real-Debrid violet
    val Usenet = Color(0xFF22D3EE)  // Usenet cyan
    val Emby = Color(0xFF52B54B)    // Emby green
    val Jellyfin = Color(0xFF00A4DC)
    val Plex = Color(0xFFE5A00D)    // Plex amber
    val Http = Color(0xFF94A3B8)    // HTTP slate
    val P2p = Color(0xFFF472B6)     // P2P pink
}

/**
 * Quality-tier accent colors for badges.
 */
object OmnioQualityColors {
    val FourK = Color(0xFFFFD166)   // 4K / 4K HDR — warm gold
    val Hdr = Color(0xFFC7B6FF)     // HDR — soft lavender
    val FullHd = Color(0xFFA7F3D0)  // 1080 — mint
    val Hd = Color(0x80FFFFFF)      // 720 — white @ 50%
}
