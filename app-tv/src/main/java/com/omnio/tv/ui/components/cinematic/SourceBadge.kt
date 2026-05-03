package com.omnio.tv.ui.components.cinematic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.omnio.tv.core.uishared.OmnioSourceColors

/**
 * Outlined chip for an addon/source: dot in source color + uppercase mono
 * short-name. Composites over poster artwork via a 60% black backplate.
 */
@Composable
fun SourceBadge(
    src: SourceKind,
    modifier: Modifier = Modifier,
    size: SourceBadgeSize = SourceBadgeSize.Md
) {
    val color = src.color
    val m = size.metrics()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(
                width = 1.5.dp,
                color = color,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(PaddingValues(horizontal = m.paddingH, vertical = m.paddingV)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.gap)
    ) {
        Box(
            modifier = Modifier
                .size(m.dotSize)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = src.shortName,
            color = color,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = m.fontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = (m.fontSize.value * 0.08f).sp
            )
        )
    }
}

enum class SourceKind(val shortName: String, val displayName: String, val color: Color) {
    Rd("RD", "Real-Debrid", OmnioSourceColors.Rd),
    Usenet("NZB", "Usenet", OmnioSourceColors.Usenet),
    Emby("EMBY", "Emby", OmnioSourceColors.Emby),
    Jellyfin("JF", "Jellyfin", OmnioSourceColors.Jellyfin),
    Plex("PLEX", "Plex", OmnioSourceColors.Plex),
    Http("HTTP", "HTTP Direct", OmnioSourceColors.Http),
    P2p("P2P", "Torrent", OmnioSourceColors.P2p);

    companion object {
        /** Best-effort match against the addon-name strings the rest of the app uses. */
        fun fromAddonName(name: String?): SourceKind? {
            if (name.isNullOrBlank()) return null
            val n = name.lowercase()
            return when {
                "real-debrid" in n || "realdebrid" in n || n == "rd" -> Rd
                "usenet" in n || "nzb" in n -> Usenet
                "emby" in n -> Emby
                "jellyfin" in n -> Jellyfin
                "plex" in n -> Plex
                "torrent" in n || "p2p" in n || "peer" in n -> P2p
                "http" in n || "direct" in n -> Http
                else -> null
            }
        }
    }
}

enum class SourceBadgeSize { Sm, Md, Lg }

internal data class SourceBadgeMetrics(
    val fontSize: TextUnit,
    val paddingV: Dp,
    val paddingH: Dp,
    val dotSize: Dp,
    val gap: Dp
)

internal fun SourceBadgeSize.metrics(): SourceBadgeMetrics = when (this) {
    SourceBadgeSize.Sm -> SourceBadgeMetrics(10.sp, 2.dp, 6.dp, 4.dp, 4.dp)
    SourceBadgeSize.Md -> SourceBadgeMetrics(13.sp, 4.dp, 8.dp, 5.dp, 5.dp)
    SourceBadgeSize.Lg -> SourceBadgeMetrics(18.sp, 6.dp, 14.dp, 8.dp, 7.dp)
}
