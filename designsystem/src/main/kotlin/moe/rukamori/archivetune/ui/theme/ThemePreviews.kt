/*
 * YumaPlayer (2026) | work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
// 1. Светлая тема
@Preview(
    name = "Light",
    group = "Themes",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
)
// 2. Тёмная тема
@Preview(
    name = "Dark",
    group = "Themes",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
// 3. Material 3 Dynamic Color (симуляция цвета обоев)
@Preview(
    name = "M3 Dynamic (Blue)",
    group = "Dynamic",
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
    showBackground = true,
)
@Preview(
    name = "M3 Dynamic (Red)",
    group = "Dynamic",
    wallpaper = Wallpapers.RED_DOMINATED_EXAMPLE,
    showBackground = true,
)
annotation class ThemePreviews

@Composable
fun TestThemeWrapper(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
    ) {
        ArchiveTuneTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

@ThemePreviews
@Composable
fun PseudoGlassPreview() {
    TestThemeWrapper {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val positions = listOf(
                YumaSegmentPosition.Single,
                YumaSegmentPosition.First,
                YumaSegmentPosition.Middle,
                YumaSegmentPosition.Last,
            )
            positions.forEach { position ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pseudoGlass(position = position),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = position.name,
                        color = LocalYumaColors.current.textPrimary
                    )
                }
            }
        }
    }
}
