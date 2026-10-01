package moe.rukamori.archivetune

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.theme.ColorSaver
import moe.rukamori.archivetune.ui.theme.DefaultThemeColor
import moe.rukamori.archivetune.ui.theme.ThemeSeedPalette
import moe.rukamori.archivetune.ui.theme.ThemeSeedPaletteCodec
import moe.rukamori.archivetune.ui.theme.extractSeedColor

data class DynamicThemeColorState(
    val themeColor: Color,
    val seedPalette: ThemeSeedPalette?,
)

@Composable
fun rememberDynamicThemeColor(
    activity: ComponentActivity,
    playerConnection: PlayerConnection?,
    enableDynamicTheme: Boolean,
    customThemeColorValue: String,
    isSystemInDarkTheme: Boolean = isSystemInDarkTheme(),
): DynamicThemeColorState {
    val customThemeSeedPalette =
        remember(customThemeColorValue) {
            if (customThemeColorValue.startsWith("seedPalette:")) {
                ThemeSeedPaletteCodec.decodeFromPreference(customThemeColorValue)
            } else {
                null
            }
        }

    val customThemeColor =
        remember(customThemeColorValue, customThemeSeedPalette) {
            if (customThemeColorValue.startsWith("#")) {
                try {
                    val colorString = customThemeColorValue.removePrefix("#")
                    Color(android.graphics.Color.parseColor("#$colorString"))
                } catch (e: Exception) {
                    DefaultThemeColor
                }
            } else {
                customThemeSeedPalette?.primary ?: DefaultThemeColor
            }
        }

    var themeColor by rememberSaveable(stateSaver = ColorSaver) {
        mutableStateOf(DefaultThemeColor)
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, customThemeColor) {
        val playerConnection = playerConnection
        if (!enableDynamicTheme || playerConnection == null) {
            themeColor = if (!enableDynamicTheme) customThemeColor else DefaultThemeColor
            return@LaunchedEffect
        }
        playerConnection.service.currentMediaMetadata.collectLatest { song ->
            if (song != null) {
                withContext(Dispatchers.Default) {
                    try {
                        val result =
                            activity.imageLoader.execute(
                                ImageRequest
                                    .Builder(activity.applicationContext)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .build(),
                            )
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val accurateSeed = extractSeedColor(bitmap)
                            withContext(Dispatchers.Main) {
                                themeColor = accurateSeed
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                themeColor = DefaultThemeColor
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            themeColor = DefaultThemeColor
                        }
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    themeColor = DefaultThemeColor
                } else {
                    themeColor = customThemeColor
                }
            }
        }
    }

    return DynamicThemeColorState(
        themeColor = themeColor,
        seedPalette = customThemeSeedPalette,
    )
}
