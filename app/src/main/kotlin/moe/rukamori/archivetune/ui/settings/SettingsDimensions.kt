/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.settings

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rukamori.archivetune.LocalAnimationsDisabled

/**
 * Single source of truth for every design value used across settings screens.
 * Consumers must read values from here instead of hardcoding them.
 */
object SettingsDimensions {

    val SafeImmersiveMinBottom: Dp = 16.dp
    val FloatingBarBottomSpacing: Dp = 8.dp
    val PlayerControlsHorizontalPadding: Dp = 16.dp
    val PlayerControlsVerticalGap: Dp = 16.dp

    // -- Cards -------------------------------------------------------------
    val CardElevation = 0.dp
    val GroupCardCornerRadius = 18.dp
    val BannerCardCornerRadius = 18.dp

    // -- Screen layout -----------------------------------------------------
    val ScreenHorizontalPadding = 16.dp
    val ScreenBottomPadding = 24.dp
    val SectionSpacing = 12.dp
    val RowVerticalPadding = 10.dp
    val RowHorizontalPadding = 14.dp

    // -- Row icons ---------------------------------------------------------
    val RowIconSize = 32.dp
    val RowIconInnerSize = 20.dp
    val BannerIconSize = 46.dp
    val BannerIconInnerSize = 24.dp
    val ChevronSize = 18.dp

    // -- Profile card ------------------------------------------------------
    val ProfileCardAvatarSize = 52.dp
    val ProfileCardAvatarIconSize = 24.dp

    // -- Dividers ----------------------------------------------------------
    val DividerThickness = 0.5.dp
    val DividerStartIndent = 60.dp

    // -- Section headers ---------------------------------------------------
    val SectionHeaderBottomPadding = 6.dp
    val SectionHeaderHorizontalPadding = 20.dp

    // -- Segmented groups (settings list) ----------------------------------
    val SegmentedGroupHorizontalPadding = 16.dp
    val SegmentedItemGap = 1.5.dp
    val SegmentedCornerLarge = 22.dp
    val SegmentedCornerSmall = 5.dp
    val SegmentedItemMinHeight = 72.dp
    val SegmentedItemPaddingHorizontal = 18.dp
    val SegmentedItemPaddingVertical = 12.dp
    val SegmentedIconBoxSize = 46.dp
    val SegmentedIconSize = 25.dp
    val SegmentedIconSpacing = 16.dp
    val SegmentedBadgeSize = 8.dp
    val SegmentedRowSpacing = 2.dp
    val SegmentedBadgeSpacing = 10.dp
    val SegmentedBadgeCornerPercent = 50
    val SegmentedBadgePaddingH = 8.dp
    val SegmentedBadgePaddingV = 4.dp

    // -- Settings row ------------------------------------------------------
    val RowIconBgAlpha = 0.12f
    val RowTextSpacing = 1.dp
    val RowIconSpacing = 14.dp
    val RowChevronSpacing = 4.dp
    val RowChevronAlpha = 0.3f
    val DividerAlpha = 0.3f
    val BadgeCornerRadius = 8.dp
    val BadgePaddingH = 8.dp
    val BadgePaddingV = 4.dp
    val BadgeSpacing = 6.dp
    val BadgeSize = 8.dp

    // -- Banners & profile header ------------------------------------------
    val BannerContentPadding = 16.dp
    val BannerIconSpacing = 14.dp
    val BannerColumnSpacing = 2.dp
    val BannerButtonPaddingH = 16.dp
    val BannerButtonPaddingV = 8.dp
    val BannerCloseIconSize = 18.dp
    val BannerTextSpacing = 12.dp
    val BannerIconBgAlpha = 0.12f
    val BannerIconBgAlphaSoft = 0.10f
    val BannerSubtitleAlpha = 0.7f
    val BannerVersionAlpha = 0.75f
    val BannerCloseAlpha = 0.7f
    val ProfileChevronAlpha = 0.5f
    val ProfileAvatarBgAlpha = 0.12f

    // -- Flat items --------------------------------------------------------
    val FlatPaddingH = 16.dp
    val FlatPaddingV = 14.dp
    val FlatIconStartPadding = 8.dp
    val FlatIconEndPadding = 16.dp
    val FlatTitleSize = 19.sp
    val FlatSubtitleAlpha = 0.7f
    val FlatBadgeSpacing = 6.dp

    // -- Screen background gradient ----------------------------------------
    val BackgroundTopAlpha = 0.22f
    val BackgroundMidAlpha = 0.06f

    // -- Yuma glass preferences --------------------------------------------
    val GlassCornerRadius = 18.dp
    val GlassBorderThickness = 0.5.dp
    val GlassStartColor = Color(0x1AFFFFFF)
    val GlassEndColor = Color(0x08FFFFFF)

    // -- Bottom Sheet ------------------------------------------------------
    val BottomSheetHorizontalPadding = 16.dp
    val BottomSheetBottomPadding = 20.dp
    val BottomSheetCornerRadius = 28.dp
    val BottomSheetContentPaddingH = 20.dp
    val BottomSheetContentPaddingTop = 16.dp
    val BottomSheetContentPaddingBottom = 24.dp
    val BottomSheetDragHandleWidth = 40.dp
    val BottomSheetDragHandleHeight = 4.dp
    val BottomSheetDragHandleBottomPadding = 12.dp
    val BottomSheetTitleBottomPadding = 16.dp
    val BottomSheetListMaxHeight = 480.dp
    val BottomSheetListCornerRadius = 24.dp
    val BottomSheetOptionPaddingH = 20.dp
    val BottomSheetOptionPaddingV = 14.dp
    val BottomSheetOptionIconSize = 20.dp
    val BottomSheetOptionIconSpacing = 12.dp
    val YumaTitleFontSize = 12.sp
    val YumaTitlePaddingStart = 4.dp
    val YumaTitlePaddingBottom = 8.dp
    val YumaCategorySpacing = 8.dp
    val YumaRowIconSize = 26.dp
    val YumaRowIconSpacing = 12.dp
    val YumaRowArrowSize = 24.dp
    val YumaRowArrowSpacing = 8.dp
    val YumaRowTextSpacing = 2.dp
    val YumaRowTitleSize = 15.sp
    val YumaRowSubtitleSize = 11.sp
    val YumaRowSubtitleLineHeight = 14.sp
    val YumaRowIconAlpha = 0.8f
    val YumaRowSubtitleAlpha = 0.65f
    val YumaRowDisabledAlpha = 0.5f
    val YumaRowArrowAlpha = 0.3f
    val YumaSwitchUncheckedThumbAlpha = 0.6f
    val YumaSwitchTrackAlpha = 0.3f
    val YumaSwitchUncheckedTrackColor = Color(0x0DFFFFFF)

    // -- Library (YDS) -------------------------------------------------------
    val LibraryChipHeight = 36.dp
    val LibraryBadgeSize = 46.dp
    val LibraryCardRadius = 16.dp
    val LibrarySheetRadius = 24.dp
    val LibrarySmallRadius = 12.dp
}

object SettingsAnimations {
    // User's Yuma press feedback: slight shrink on press.
    val PressScale = 0.96f
    val PressDampingRatio = 0.6f
    val PressStiffness = Spring.StiffnessMedium

    @Composable
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (LocalAnimationsDisabled.current) {
            snap()
        } else {
            spring(dampingRatio = PressDampingRatio, stiffness = PressStiffness)
        }
}
