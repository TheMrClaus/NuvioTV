package com.omnio.tv.ui.components.cinematic

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.omnio.tv.core.uishared.OmnioColors
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
        val meta = remember(display.rawDescription) { parseStreamMeta(display.rawDescription) }
        val titleText = meta.filename ?: display.rawName

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                display.sourceKind?.let { SourceBadge(src = it, size = SourceBadgeSize.Md) }
            }
            Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.CenterStart) {
                display.quality?.let { QualityBadge(it) }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.infoSegments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = meta.infoSegments.joinToString("   "),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val hasRightMeta = meta.sizeText != null ||
                meta.bitrateText != null ||
                meta.seedersText != null ||
                meta.statusText != null
            if (hasRightMeta) {
                Column(
                    modifier = Modifier.widthIn(min = 96.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    meta.sizeText?.let {
                        Text(
                            text = it,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                    }
                    meta.bitrateText?.let {
                        Text(
                            text = it,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                    if (meta.seedersText != null || meta.statusText != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            meta.seedersText?.let {
                                Text(
                                    text = "🌱 $it",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                            meta.statusText?.let {
                                Text(
                                    text = it,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (meta.statusIsError) OmnioColors.Error else OmnioColors.Success
                                )
                            }
                        }
                    }
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

internal data class StreamMeta(
    val filename: String?,
    val infoSegments: List<String>,
    val sizeText: String?,
    val bitrateText: String?,
    val seedersText: String?,
    val statusText: String?,
    val statusIsError: Boolean
)

internal fun parseStreamMeta(description: String?): StreamMeta {
    val empty = StreamMeta(null, emptyList(), null, null, null, null, false)
    if (description.isNullOrBlank()) return empty

    val lines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) return empty

    val filename = lines.first()
    val segmentDelimiter = Regex("""\s{2,}|\s[·•|]\s""")
    val segments = lines.drop(1).flatMap { line ->
        line.split(segmentDelimiter).map { it.trim() }.filter { it.isNotBlank() }
    }

    val sizeRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*(GiB|GB|MiB|MB|TiB|TB|KiB|KB)\b""",
        RegexOption.IGNORE_CASE
    )
    val bitrateRegex = Regex(
        """(\d+(?:[.,]\d+)?)\s*([KMG]bps)\b""",
        RegexOption.IGNORE_CASE
    )

    var sizeText: String? = null
    var bitrateText: String? = null
    var seedersText: String? = null
    var statusText: String? = null
    var statusIsError = false
    val infoSegments = mutableListOf<String>()

    for (seg in segments) {
        val sizeMatch = if (sizeText == null) sizeRegex.find(seg) else null
        val bitrateMatch = if (bitrateText == null) bitrateRegex.find(seg) else null
        when {
            sizeMatch != null -> {
                sizeText = "${sizeMatch.groupValues[1].replace(',', '.')} ${sizeMatch.groupValues[2].uppercase()}"
            }
            bitrateMatch != null -> {
                bitrateText = "${bitrateMatch.groupValues[1].replace(',', '.')} ${bitrateMatch.groupValues[2]}"
            }
            seedersText == null && seg.startsWith("🌱") -> {
                val num = Regex("""\d+""").find(seg)?.value
                if (num != null) seedersText = num else infoSegments.add(seg)
            }
            statusText == null && seg.contains("Not Ready", ignoreCase = true) -> {
                statusText = stripLeadingEmoji(seg)
                statusIsError = true
            }
            statusText == null && seg.contains("Ready", ignoreCase = true) -> {
                statusText = stripLeadingEmoji(seg)
                statusIsError = false
            }
            else -> infoSegments.add(seg)
        }
    }

    return StreamMeta(filename, infoSegments, sizeText, bitrateText, seedersText, statusText, statusIsError)
}

private fun stripLeadingEmoji(s: String): String {
    var i = 0
    while (i < s.length && !s[i].isLetterOrDigit() && s[i] != '(') i++
    return s.substring(i).trim()
}
