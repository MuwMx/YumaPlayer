/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5
val FloatingToolbarHeight = 72.dp
val FloatingToolbarHorizontalPadding = 16.dp
val FloatingToolbarBottomPadding = 8.dp
val NavigationBarHeight = FloatingToolbarHeight
val MiniPlayerHeight = 64.dp
val MiniPlayerBottomSpacing = 8.dp // Space between MiniPlayer and NavigationBar
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 72.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 56.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 10.dp
val GridThumbnailCornerRadius = 8.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

val BottomSheetAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

val BottomSheetSoftAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

// Полный статический отступ плеера со всеми зазорами, когда панель навигации ОТКРЫТА
val MiniPlayerWithNavBarOffset =
    FloatingToolbarBottomPadding + // 8.dp
            FloatingToolbarHeight +        // 72.dp
            MiniPlayerBottomSpacing +      // 8.dp
            MiniPlayerHeight               // 64.dp (Итого: 152.dp)

// Чистый статический отступ плеера, когда панель навигации СКРЫТА
val MiniPlayerOnlyOffset =
    MiniPlayerBottomSpacing +      // 8.dp
            MiniPlayerHeight               // 64.dp (Итого: 72.dp)
