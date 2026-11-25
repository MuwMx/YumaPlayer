/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun SegmentedArtistChart(
    artists: List<Artist>,
    totalTimeListened: Long,
    modifier: Modifier = Modifier,
) {
    val visibleArtistTime = remember(artists) { artists.sumOf { it.timeListened?.toLong() ?: 0L } }
    val displayTotalTime =
        remember(totalTimeListened, visibleArtistTime) {
            totalTimeListened.takeIf { it > 0L } ?: visibleArtistTime
        }
    val chartTotalTime =
        remember(displayTotalTime, visibleArtistTime) {
            maxOf(displayTotalTime, visibleArtistTime)
        }
    if (chartTotalTime == 0L) return

    val segmentData =
        remember(artists, chartTotalTime) {
            var startAngle = -90f
            artists.mapNotNull { artist ->
                val time = artist.timeListened?.toLong() ?: 0L
                val sweep = (time.toFloat() / chartTotalTime) * 360f
                if (sweep < 1f) return@mapNotNull null
                val entry = Triple(artist, startAngle, sweep)
                startAngle += sweep
                entry
            }
        }
    val visibleSegmentSweep =
        remember(segmentData) {
            segmentData.sumOf { it.third.toDouble() }.toFloat()
        }
    val remainingSweep =
        remember(visibleSegmentSweep) {
            (360f - visibleSegmentSweep)
                .takeIf { it >= 1f }
        }

    val segmentColors =
        remember {
            listOf(
                Color(0xFF6750A4),
                Color(0xFF4A8FA8),
                Color(0xFF4CAF50),
                Color(0xFFFF9800),
                Color(0xFFE91E63),
            )
        }
    val remainingColor = MaterialTheme.colorScheme.surfaceVariant

    ElevatedCard(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(140.dp)
                        .drawWithCache {
                            val strokeWidth = size.width * 0.18f
                            val inset = strokeWidth / 2f
                            val arcRect =
                                Rect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                )
                            onDrawBehind {
                                segmentData.forEachIndexed { i, (_, startAngle, sweep) ->
                                    val gapDeg = if (segmentData.size > 1) 2f else 0f
                                    drawArc(
                                        color = segmentColors[i % segmentColors.size],
                                        startAngle = startAngle + gapDeg / 2f,
                                        sweepAngle = (sweep - gapDeg).coerceAtLeast(0f),
                                        useCenter = false,
                                        topLeft = arcRect.topLeft,
                                        size = Size(arcRect.width, arcRect.height),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                                remainingSweep?.let { sweep ->
                                    drawArc(
                                        color = remainingColor,
                                        startAngle = -90f + visibleSegmentSweep,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        topLeft = arcRect.topLeft,
                                        size = Size(arcRect.width, arcRect.height),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    )
                                }
                            }
                        },
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                segmentData.forEachIndexed { i, (artist, _, sweep) ->
                    val percentage = (sweep / 360f * 100).toInt()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(segmentColors[i % segmentColors.size]),
                        )
                        Text(
                            text = artist.artist.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val context = LocalContext.current
                Text(
                    text = makeTimeString(displayTotalTime, context) ?: "-",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.stats_total_time_listened),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
