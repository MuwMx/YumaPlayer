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
import androidx.compose.ui.unit.sp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5
val FloatingToolbarHeight = 56.dp
val FloatingToolbarHorizontalPadding = 16.dp
val FloatingToolbarBottomPadding = 8.dp

// --- Navigation Bar Tokens (Telegram-inspired) ---
// Bar shell geometry
val NavigationBarHeight = 56.dp
val NavigationBarMaxWidth = 394.dp
val NavigationBarCornerRadius = 28.dp
val NavigationBarInnerPaddingHorizontal = 4.dp
val NavigationBarInnerPaddingVertical = 0.dp

// Bar shadow
val NavigationBarShadowDy = 0.85.dp
val NavigationBarShadowBlur = 2.67.dp
const val NavigationBarShadowAlpha = 0.15f
const val NavigationBarShadowLayerAlpha = 0.20f

// Bar hide physics (translation + scale + fade driven by visibility factor)
val NavigationBarHideOffsetY = 40.dp
const val NavigationBarHideMinScale = 0.85f
const val NavigationBarVisibilityDurationMs = 380

// Tab slots and content
val NavigationTabSlotPaddingHorizontal = 0.dp
val NavigationTabSlotPaddingVertical = 2.dp
val NavigationTabIconSize = 22.dp
val NavigationTabLabelSize = 11.sp
val NavigationTabLabelTopPadding = 1.dp
val NavigationTabContentPaddingVertical = 2.dp
const val NavigationTabPressedScale = 0.95f
const val NavigationTabSelectorVisibleThreshold = 0.001f

// Tab selector island
const val NavigationTabSelectorAlpha = 0.09f
const val NavigationTabSelectorInitialScale = 0.85f
const val NavigationTabSelectionDurationMs = 320
const val NavigationTabContentDurationMs = 250

// Overflow menu slot
val NavigationBarOverflowPaddingHorizontal = 4.dp


val MiniPlayerHeight = 64.dp
val MiniPlayerBottomSpacing = 4.dp // Space between MiniPlayer and NavigationBar
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

val MiniPlayerWithNavBarOffset =
    FloatingToolbarBottomPadding +
            FloatingToolbarHeight +
            MiniPlayerBottomSpacing +
            MiniPlayerHeight

val MiniPlayerOnlyOffset =
    MiniPlayerBottomSpacing +
            MiniPlayerHeight
