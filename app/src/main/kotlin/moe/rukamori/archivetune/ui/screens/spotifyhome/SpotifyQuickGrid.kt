/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.spotifyhome

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.glassBorder
import moe.rukamori.archivetune.ui.theme.yumaClickable

@Composable
internal fun <T> SpotifyQuickGrid(
    items: List<T>,
    maxItems: Int = 8,
    columns: Int = 2,
    itemContent: @Composable (T) -> Unit
) {
    val displayItems = items.take(maxItems)
    if (displayItems.isEmpty()) return

    val rows = displayItems.chunked(columns)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        itemContent(item)
                    }
                }
                val emptyCells = columns - rowItems.size
                repeat(emptyCells) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun SpotifyQuickGridCell(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    isArtist: Boolean
) {
    val cardShape = RoundedCornerShape(SettingsDimensions.BadgeCornerRadius)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .yumaClickable(onClick = onClick)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .glassBorder(shape = cardShape, strokeWidth = SettingsDimensions.GlassBorderThickness)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(
                    if (isArtist) {
                        CircleShape
                    } else {
                        RoundedCornerShape(
                            topStart = SettingsDimensions.BadgeCornerRadius,
                            bottomStart = SettingsDimensions.BadgeCornerRadius
                        )
                    }
                )
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
