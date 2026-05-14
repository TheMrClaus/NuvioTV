package com.omnio.tv.ui.components.cinematic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.omnio.tv.ui.theme.InterFamily

/**
 * Mark + "omnio" wordmark, sized off [height] (the cap-height of the text).
 * Mirrors the React OmnioWordmark: mark = 1.1× text height, gap = 0.25× text
 * height, text weight 800, letter-spacing -0.04em.
 */
@Composable
fun OmnioWordmark(
    height: Dp = 48.dp,
    color: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val markSize = height * 1.1f
    val gap = height * 0.25f
    val fontSize = height.value.sp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        OmnioMark(size = markSize)
        Text(
            text = "omnio",
            color = color,
            style = TextStyle(
                fontFamily = InterFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                letterSpacing = (-0.04f * fontSize.value).sp,
                lineHeight = fontSize
            )
        )
    }
}
