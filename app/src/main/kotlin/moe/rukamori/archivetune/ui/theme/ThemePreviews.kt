/*
 * YumaPlayer (2026) | work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
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
