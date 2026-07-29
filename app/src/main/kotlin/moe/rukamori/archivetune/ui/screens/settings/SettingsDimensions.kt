/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled

object SettingsDimensions {
    val GroupCardCornerRadius = 20.dp
    val BannerCardCornerRadius = 18.dp

    val ScreenHorizontalPadding = 16.dp
    val ScreenBottomPadding = 32.dp
    val SectionSpacing = 8.dp
    val RowVerticalPadding = 12.dp
    val RowHorizontalPadding = 16.dp

    val RowIconSize = 32.dp
    val RowIconInnerSize = 20.dp
    val BannerIconSize = 40.dp
    val BannerIconInnerSize = 20.dp
    val ChevronSize = 20.dp

    val ProfileCardAvatarSize = 52.dp
    val ProfileCardAvatarIconSize = 26.dp

    val DividerThickness = 0.5.dp
    val DividerStartIndent = 52.dp

    val SectionHeaderBottomPadding = 6.dp
    val SectionHeaderHorizontalPadding = 16.dp
}

object SettingsAnimations {
    val PressScale = 0.97f

    @Composable
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (LocalAnimationsDisabled.current) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        }
}
