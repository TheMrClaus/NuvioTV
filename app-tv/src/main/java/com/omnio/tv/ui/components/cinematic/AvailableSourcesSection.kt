package com.omnio.tv.ui.components.cinematic

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextAlign
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
    val codec: String?,
    val audio: String?,
    val size: String?,
    val label: String
)

/**
 * Rank a list of streams by quality > size > original-order, returning a list
 * of [StreamRowDisplay] with parsed codec/audio/size labels and a tier
 * assignment for the design's red-wash "best" highlight on the top picks.
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
            codec = p.codec,
            audio = p.audio,
            size = p.sizeLabel,
            label = p.label
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
            Text(
                text = display.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MonoCell(text = display.codec, width = 90, alignEnd = false)
            MonoCell(text = display.audio, width = 110, alignEnd = false)
            MonoCell(
                text = display.size,
                width = 76,
                alignEnd = true,
                color = Color.White
            )
        }
    }
}

@Composable
private fun MonoCell(
    text: String?,
    width: Int,
    alignEnd: Boolean,
    color: Color = Color.White.copy(alpha = 0.72f)
) {
    Text(
        text = text ?: "—",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        ),
        color = color,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width.dp)
    )
}

private data class ParsedStream(
    val stream: Stream,
    val originalIndex: Int,
    val sourceKind: SourceKind?,
    val quality: String?,
    val codec: String?,
    val audio: String?,
    val sizeBytes: Long?,
    val sizeLabel: String?,
    val label: String
)

private fun parseStream(stream: Stream, idx: Int): ParsedStream {
    val text = listOfNotNull(stream.name, stream.title, stream.description).joinToString(" ")
    val upper = text.uppercase()
    val quality = parseQuality(upper)
    val codec = parseCodec(upper)
    val audio = parseAudio(upper)
    val bytes = stream.behaviorHints?.videoSize ?: parseSizeFromText(text)
    val sourceKind = SourceKind.fromAddonName(stream.sourceProvider)
        ?: SourceKind.fromAddonName(stream.addonName)
        ?: if (stream.isTorrent()) SourceKind.P2p else null
    return ParsedStream(
        stream = stream,
        originalIndex = idx,
        sourceKind = sourceKind,
        quality = quality,
        codec = codec,
        audio = audio,
        sizeBytes = bytes,
        sizeLabel = formatSize(bytes),
        label = stream.getDisplayName()
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

private fun parseCodec(upper: String): String? {
    val core = when {
        "AV1" in upper -> "AV1"
        "HEVC" in upper || "H.265" in upper || "H265" in upper || "X265" in upper || "X.265" in upper -> "H.265"
        "AVC" in upper || "H.264" in upper || "H264" in upper || "X264" in upper -> "H.264"
        "VP9" in upper -> "VP9"
        else -> return null
    }
    val dv = "DOLBY VISION" in upper || " DV " in " $upper " || ".DV." in upper
    return if (dv) "$core · DV" else core
}

private fun parseAudio(upper: String): String? {
    val format = when {
        "ATMOS" in upper -> "Atmos"
        "TRUEHD" in upper -> "TrueHD"
        "DTS-HD MA" in upper || "DTS HD MA" in upper -> "DTS-HD MA"
        "DTS-HD" in upper || "DTS HD" in upper -> "DTS-HD"
        "DTS" in upper -> "DTS"
        "DDP" in upper || "DD+" in upper || "EAC3" in upper || "EAC-3" in upper -> "DDP"
        "AC3" in upper || "AC-3" in upper -> "AC3"
        "AAC" in upper -> "AAC"
        else -> null
    }
    val channels = when {
        "7.1" in upper -> "7.1"
        "5.1" in upper -> "5.1"
        "2.0" in upper -> "2.0"
        else -> null
    }
    return when {
        format != null && channels != null -> "$format $channels"
        format != null -> format
        channels != null -> channels
        else -> null
    }
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

private fun formatSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val gb = bytes / 1_073_741_824.0
    return if (gb >= 1.0) {
        String.format("%.1f GB", gb)
    } else {
        val mb = bytes / 1_048_576.0
        String.format("%.0f MB", mb)
    }
}
