/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    var progressProvider: () -> Float by mutableStateOf({ 0f })

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss() {
        isVisible = false
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val focusManager = LocalFocusManager.current

    if (state.isVisible) {
        val sheetState = rememberModalBottomSheetState()
        var fullHeight by remember { mutableFloatStateOf(0f) }

        DisposableEffect(state, sheetState) {
            state.progressProvider = {
                if (fullHeight <= 0f) {
                    0f
                } else {
                    val currentOffset = runCatching { sheetState.requireOffset() }.getOrDefault(fullHeight)
                    ((fullHeight - currentOffset) / fullHeight).coerceIn(0f, 1f)
                }
            }
            onDispose {
                state.progressProvider = { 0f }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = null,
            modifier = modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 48.dp)
                .onSizeChanged { fullHeight = it.height.toFloat() },
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(SettingsDimensions.BottomSheetCornerRadius),
                color = background,
                border = BorderStroke(
                    width = SettingsDimensions.GlassBorderThickness,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
                tonalElevation = 6.dp,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    )
                    state.content(this)
                }
            }
        }
    }
}
