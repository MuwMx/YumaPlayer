package moe.rukamori.archivetune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.ui.theme.TestThemeWrapper
import moe.rukamori.archivetune.ui.theme.ThemePreviews

object PlayerDockDefaults {
    val MaxWidth: Dp = 380.dp
    val HorizontalPadding: Dp = 16.dp
    val SlotGap: Dp = 2.dp
}

@Composable
fun PlayerDockContainer(
    modifier: Modifier = Modifier,
    miniPlayerSlot: @Composable () -> Unit = {},
    barSlot: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .widthIn(max = PlayerDockDefaults.MaxWidth)
            .fillMaxWidth()
            .padding(horizontal = PlayerDockDefaults.HorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PlayerDockDefaults.SlotGap),
    ) {
        miniPlayerSlot()
        barSlot()
    }
}

@ThemePreviews
@Composable
private fun PlayerDockContainerPreview() {
    TestThemeWrapper {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            PlayerDockContainer(
                miniPlayerSlot = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "MiniPlayer Pill Slot")
                    }
                },
                barSlot = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "Navigation Bar Slot")
                    }
                },
            )
        }
    }
}
