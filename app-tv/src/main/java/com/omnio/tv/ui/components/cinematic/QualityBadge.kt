package com.omnio.tv.ui.components.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.omnio.tv.core.uishared.OmnioQualityColors

/**
 * Quality-tier pill: "4K HDR", "4K", "1080", "720" rendered in mono with a
 * tinted soft-fill + stroked border drawn from [OmnioQualityColors].
 */
@Composable
fun QualityBadge(
    quality: String,
    modifier: Modifier = Modifier
) {
    val color = colorFor(quality)
    Text(
        text = quality.uppercase(),
        color = color,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.05.em
        ),
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.12f))
            .border(width = 1.dp, color = color.copy(alpha = 0.40f), shape = RoundedCornerShape(3.dp))
            .padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp))
    )
}

private fun colorFor(quality: String): Color {
    val q = quality.uppercase()
    return when {
        "4K" in q -> OmnioQualityColors.FourK
        "HDR" in q -> OmnioQualityColors.Hdr
        "1080" in q -> OmnioQualityColors.FullHd
        "720" in q -> OmnioQualityColors.Hd
        else -> Color.White.copy(alpha = 0.9f)
    }
}
