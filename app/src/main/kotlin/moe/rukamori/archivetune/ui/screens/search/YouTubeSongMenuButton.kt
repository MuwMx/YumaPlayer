/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu

@Composable
internal fun YouTubeSongMenuButton(
    song: SongItem,
    navController: NavController,
) {
    val menuState = LocalMenuState.current
    IconButton(
        onClick = {
            menuState.show {
                YouTubeSongMenu(
                    song = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
        )
    }
}
@Composable
internal fun SearchDiscoveryLoading(modifier: Modifier = Modifier) {
    ShimmerHost(
        modifier = modifier.fillMaxWidth(),
    ) {
        TextPlaceholder(
            height = 56.dp,
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
        )
        TextPlaceholder(
            height = 28.dp,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .width(180.dp),
        )
        repeat(6) {
            TextPlaceholder(
                height = 84.dp,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth(),
            )
        }
    }
}
@Composable
internal fun SearchStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout
                .Row(content = action)
        }
    }
}
