/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.haptics.rememberYumaHaptics

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.OnlineSearchResultRow(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberYumaHaptics()
    YouTubeListItem(
        item = item,
        isActive = isActive,
        isPlaying = isPlaying,
        trailingContent = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                )
            }
        },
        modifier =
            modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.longPress()
                        onMenuClick()
                    },
                ).animateItem(),
    )
}
