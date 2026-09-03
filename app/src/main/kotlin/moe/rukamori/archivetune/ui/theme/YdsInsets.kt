package moe.rukamori.archivetune.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

object YdsInsets {
    val ydsSafeBottom: WindowInsets
        @Composable
        get() = WindowInsets.navigationBars.union(
            WindowInsets(bottom = SettingsDimensions.SafeImmersiveMinBottom)
        )

    val ydsSafeContent: WindowInsets
        @Composable
        get() = WindowInsets.safeDrawing.union(WindowInsets.displayCutout)

    @Composable
    fun safeBottomPadding(): Dp {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return max(navBarBottom, SettingsDimensions.SafeImmersiveMinBottom)
    }

    @Composable
    fun floatingToolbarBottomPadding(): Dp {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        return navBarBottom + SettingsDimensions.FloatingBarBottomSpacing
    }
}

@Composable
fun Modifier.ydsSafeBottomPadding(): Modifier =
    windowInsetsPadding(YdsInsets.ydsSafeBottom.only(WindowInsetsSides.Bottom))

@Composable
fun Modifier.ydsSafeContentPadding(): Modifier =
    windowInsetsPadding(YdsInsets.ydsSafeContent)
