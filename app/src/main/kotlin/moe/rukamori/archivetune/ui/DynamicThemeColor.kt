package moe.rukamori.archivetune.ui

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
import androidx.compose.runtime.withFrameNanos
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
import moe.rukamori.archivetune.ui.theme.getCachedSeedColor

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
                val thumbnailUrl = song.thumbnailUrl?.trim()?.takeIf(String::isNotBlank)
                val resolvedColor =
                    withContext(Dispatchers.Default) {
                        if (thumbnailUrl == null) {
                            DefaultThemeColor
                        } else {
                            getCachedSeedColor(thumbnailUrl) ?: try {
                                val result =
                                    activity.imageLoader.execute(
                                        ImageRequest
                                            .Builder(activity.applicationContext)
                                            .data(thumbnailUrl)
                                            .size(128, 128)
                                            .memoryCacheKey("dynamic-theme:$thumbnailUrl")
                                            .diskCacheKey(thumbnailUrl)
                                            .allowHardware(false)
                                            .build(),
                                    )
                                val bitmap = result.image?.toBitmap()
                                if (bitmap != null) {
                                    extractSeedColor(bitmap, cacheKey = thumbnailUrl)
                                } else {
                                    DefaultThemeColor
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                DefaultThemeColor
                            }
                        }
                    }
                withFrameNanos { }
                themeColor = resolvedColor
            } else {
                val fallback =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        DefaultThemeColor
                    } else {
                        customThemeColor
                    }
                withFrameNanos { }
                themeColor = fallback
            }
        }
    }

    return DynamicThemeColorState(
        themeColor = themeColor,
        seedPalette = customThemeSeedPalette,
    )
}
