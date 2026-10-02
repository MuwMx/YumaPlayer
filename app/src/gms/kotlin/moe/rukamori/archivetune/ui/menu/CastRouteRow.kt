/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@Composable
internal fun CastRouteRow(
    route: CastRouteUiModel,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        selected = route.selected,
        onClick = onClick,
        enabled = route.enabled && !route.connecting,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        leadingContent = {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color =
                    if (route.selected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                contentColor =
                    if (route.selected) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Icon(
                    painter = painterResource(androidx.media3.cast.R.drawable.media_route_button_disconnected),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(22.dp),
                )
            }
        },
        trailingContent = {
            if (route.connecting) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            } else if (route.selected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supportingContent = {
            Text(
                text = route.supportingText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = route.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CastRouteUiModel.supportingText(): String =
    when {
        selected -> stringResource(R.string.together_connected)
        connecting -> stringResource(R.string.connecting)
        description != null -> description
        else -> stringResource(R.string.cast_available_device)
    }
