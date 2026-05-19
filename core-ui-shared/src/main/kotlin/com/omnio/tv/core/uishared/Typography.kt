package com.omnio.tv.core.uishared

import androidx.compose.ui.text.TextStyle

/**
 * TV-tuned typography token shape. Mirrors Material3's
 * display/headline/title/body/label scale; the TV theme adapter in
 * :app-tv ui/theme/Type.kt converts this into androidx.tv.material3.Typography.
 */
data class OmnioTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle
)
