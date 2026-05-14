package com.omnio.tv.ui.components.cinematic

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cinematic focus visual — 4dp white outline + 1.06× scale on focus, animated
 * with the design's signature cubic-bezier(0.16, 1, 0.3, 1) ease-out curve.
 *
 * Apply via `Modifier.cinematicFocus(focused = ..., shape = ...)`. Pass
 * `scale = false` for nav rails and other contexts where scaling would push
 * neighbours out of bounds.
 */
fun Modifier.cinematicFocus(
    focused: Boolean,
    cornerRadius: Dp = 6.dp,
    scale: Boolean = true,
    ringColor: Color = Color.White,
    ringWidth: Dp = 4.dp,
    durationMillis: Int = 200
): Modifier = composed {
    val targetScale = if (focused && scale) 1.06f else 1f
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis, easing = CinematicEasing),
        label = "cinematicScale"
    )
    val targetRing = if (focused) ringWidth else 0.dp
    val animatedRing by animateFloatAsState(
        targetValue = targetRing.value,
        animationSpec = tween(100),
        label = "cinematicRing"
    )

    this
        .scale(animatedScale)
        .border(
            width = animatedRing.dp,
            color = ringColor,
            shape = RoundedCornerShape(cornerRadius)
        )
}

/** cubic-bezier(0.16, 1, 0.3, 1) — the design's "focus settle" easing curve. */
val CinematicEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
