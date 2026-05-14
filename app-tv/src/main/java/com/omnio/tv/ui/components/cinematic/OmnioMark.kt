package com.omnio.tv.ui.components.cinematic

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnio.tv.R

/**
 * The Omnio mark — concentric "omni-source" rings with a red play-wedge.
 * Renders the [R.drawable.omnio_logo_mark] vector. When [animated] is true the
 * mark gently breathes (used on the onboarding welcome screen).
 */
@Composable
fun OmnioMark(
    size: Dp = 64.dp,
    animated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scale = if (animated) {
        val transition = rememberInfiniteTransition(label = "omnioMarkBreathe")
        val s by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "omnioMarkScale"
        )
        s
    } else 1f

    Image(
        painter = painterResource(id = R.drawable.omnio_logo_mark),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .scale(scale)
    )
}

/**
 * Mono variant — single tint color (defaults to white via the drawable's
 * android:tint). Useful on tinted backdrops where the gradient mark would
 * fight the background.
 */
@Composable
fun OmnioMarkMono(
    size: Dp = 64.dp,
    tint: ColorFilter? = null,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.omnio_logo_mark_mono),
        contentDescription = null,
        colorFilter = tint,
        modifier = modifier.size(size)
    )
}
