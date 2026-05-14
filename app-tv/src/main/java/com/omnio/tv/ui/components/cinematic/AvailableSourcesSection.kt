package com.omnio.tv.ui.components.cinematic

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.omnio.tv.domain.model.Stream

enum class StreamTier { Best, Good }

data class StreamRowDisplay(
    val stream: Stream,
    val tier: StreamTier,
    val sourceKind: SourceKind?,
    val quality: String?,
    val rawName: String,
    val rawDescription: String?
)

/**
 * Rank a list of streams by quality > behaviorHints videoSize > original-order,
 * returning [StreamRowDisplay] entries that carry the addon's raw name and
 * description text verbatim — no codec/audio/size parsing into structured cells.
 */
fun rankStreams(streams: List<Stream>): List<StreamRowDisplay> {
    val parsed = streams.mapIndexed { idx, stream -> parseStream(stream, idx) }
    val sorted = parsed.sortedWith(
        compareByDescending<ParsedStream> { qualityRank(it.quality) }
            .thenByDescending { it.sizeBytes ?: 0L }
            .thenBy { it.originalIndex }
    )
    val bestCount = sorted.size.coerceAtMost(2)
    return sorted.mapIndexed { idx, p ->
        StreamRowDisplay(
            stream = p.stream,
            tier = if (idx < bestCount) StreamTier.Best else StreamTier.Good,
            sourceKind = p.sourceKind,
            quality = p.quality,
            rawName = p.rawName,
            rawDescription = p.rawDescription
        )
    }
}

/** Total distinct addons represented in the streams list. */
fun streamAddonCount(streams: List<Stream>): Int =
    streams.map { it.addonName }.distinct().size

@Composable
fun AvailableSourcesHeader(
    streamCount: Int,
    addonCount: Int,
    titleText: String,
    subtitleText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = subtitleText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            ),
            color = Color.White.copy(alpha = 0.50f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AvailableSourcesRow(
    display: StreamRowDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onUpKey: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val isBest = display.tier == StreamTier.Best
    val bg = if (isBest) Color(0x0FE50914) else Color.White.copy(alpha = 0.04f)
    val restingBorder = if (isBest) Color(0x4DE50914) else Color.White.copy(alpha = 0.08f)
    val shape = RoundedCornerShape(8.dp)

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .cinematicFocus(focused = focused, cornerRadius = 8.dp, scale = false)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (onUpKey != null) Modifier.onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN && event.key == Key.DirectionUp) {
                        onUpKey(); true
                    } else false
                } else Modifier
            ),
        interactionSource = interactionSource,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = bg,
            pressedContainerColor = bg
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, restingBorder),
                shape = shape
            ),
            focusedBorder = Border(
                border = BorderStroke(1.dp, restingBorder),
                shape = shape
            )
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.width(64.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                display.sourceKind?.let { SourceBadge(src = it, size = SourceBadgeSize.Md) }
            }
            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                display.quality?.let { QualityBadge(it) }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display.rawName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
                display.rawDescription?.let { description ->
                    Text(
                        text = description,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private data class ParsedStream(
    val stream: Stream,
    val originalIndex: Int,
    val sourceKind: SourceKind?,
    val quality: String?,
    val sizeBytes: Long?,
    val rawName: String,
    val rawDescription: String?
)

private fun parseStream(stream: Stream, idx: Int): ParsedStream {
    val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")
    val upper = text.uppercase()
    val quality = parseQuality(upper)
    val bytes = stream.behaviorHints?.videoSize ?: parseSizeFromText(text)
    val sourceKind = SourceKind.fromAddonName(stream.sourceProvider)
        ?: SourceKind.fromAddonName(stream.addonName)
        ?: if (stream.isTorrent()) SourceKind.P2p else null
    val rawName = stream.getDisplayName()
    val rawDescription = stream.getDisplayDescription()?.takeIf { it != rawName && it.isNotBlank() }
    return ParsedStream(
        stream = stream,
        originalIndex = idx,
        sourceKind = sourceKind,
        quality = quality,
        sizeBytes = bytes,
        rawName = rawName,
        rawDescription = rawDescription
    )
}

private fun qualityRank(quality: String?): Int = when (quality) {
    "4K HDR" -> 4
    "4K" -> 3
    "1080" -> 2
    "720" -> 1
    else -> 0
}

private fun parseQuality(upper: String): String? = when {
    "4K" in upper && "HDR" in upper -> "4K HDR"
    "4K" in upper || "2160" in upper -> "4K"
    "1080" in upper -> "1080"
    "720" in upper -> "720"
    else -> null
}

private fun parseSizeFromText(text: String): Long? {
    val regex = Regex("""(\d+(?:[.,]\d+)?)\s*(GiB|GB|MiB|MB)""", RegexOption.IGNORE_CASE)
    val match = regex.find(text) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val unit = match.groupValues[2].uppercase()
    return when (unit) {
        "GB", "GIB" -> (value * 1_073_741_824).toLong()
        "MB", "MIB" -> (value * 1_048_576).toLong()
        else -> null
    }
}
