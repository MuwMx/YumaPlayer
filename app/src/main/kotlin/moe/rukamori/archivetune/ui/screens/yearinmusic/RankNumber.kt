/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.yearinmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.screens.RecapCream
import moe.rukamori.archivetune.ui.screens.RecapRed
import moe.rukamori.archivetune.ui.screens.RecapSurfaceHigh
import moe.rukamori.archivetune.ui.screens.RecapTokens

@Composable
internal fun RankNumber(
    rank: Int,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}
@Composable
internal fun SummaryHighlightRow(
    icon: Int,
    label: String,
    value: String,
    color: Color,
) {
    RecapStatRow(
        icon = icon,
        label = label,
        value = value,
        color = color,
    )
}
@Composable
internal fun RecapStatRow(
    icon: Int,
    label: String,
    value: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RecapTokens.SectionRadius))
                .background(RecapSurfaceHigh.copy(alpha = 0.72f))
                .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(
            icon = icon,
            background = color.copy(alpha = 0.18f),
            tint = color,
            modifier = Modifier.size(46.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RecapCream.copy(alpha = 0.58f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RecapCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
@Composable
internal fun RecapChip(
    icon: Int,
    text: String,
    color: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.2f))
                .border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.34f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = RecapCream,
            maxLines = 1,
        )
    }
}
@Composable
internal fun RecapBadge(text: String) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(RecapCream.copy(alpha = 0.14f))
                .border(
                    width = 1.dp,
                    color = RecapCream.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(RecapRed),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = RecapCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
@Composable
internal fun IconBadge(
    icon: Int,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}
