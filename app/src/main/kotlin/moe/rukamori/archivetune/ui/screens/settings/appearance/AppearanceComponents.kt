/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HomeBackgroundStyle
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.settings.SettingsDimensions

@Composable
fun DarkModeSelector(
    darkMode: DarkMode,
    onDarkModeChange: (DarkMode) -> Unit,
) {
    EnumListPreference(
        title = { Text(stringResource(R.string.dark_theme)) },
        icon = { Icon(painterResource(R.drawable.dark_mode), null, modifier = Modifier.size(24.dp)) },
        selectedValue = darkMode,
        onValueSelected = onDarkModeChange,
        valueText = {
            when (it) {
                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
            }
        },
    )
}

@Composable
fun HomeBackgroundSelector(
    homeBackgroundStyle: HomeBackgroundStyle,
    onHomeBackgroundStyleChange: (HomeBackgroundStyle) -> Unit,
) {
    EnumListPreference(
        title = { Text(stringResource(R.string.home_background)) },
        icon = { Icon(painterResource(R.drawable.image), null, modifier = Modifier.size(24.dp)) },
        selectedValue = homeBackgroundStyle,
        onValueSelected = onHomeBackgroundStyleChange,
        valueText = {
            when (it) {
                HomeBackgroundStyle.OFF -> stringResource(R.string.home_background_none)
                HomeBackgroundStyle.TONAL -> stringResource(R.string.home_background_tonal)
                HomeBackgroundStyle.CIRCLES -> stringResource(R.string.home_background_circles)
                HomeBackgroundStyle.RINGS -> stringResource(R.string.home_background_rings)
                HomeBackgroundStyle.MESH -> stringResource(R.string.home_background_mesh)
                HomeBackgroundStyle.GRID -> stringResource(R.string.home_background_grid)
                HomeBackgroundStyle.PARTICLES -> stringResource(R.string.home_background_particles)
                HomeBackgroundStyle.SNOW -> stringResource(R.string.home_background_snow)
                HomeBackgroundStyle.SPACE -> stringResource(R.string.home_background_space)
                HomeBackgroundStyle.LAVA -> stringResource(R.string.home_background_lava)
            }
        },
    )
}

@Composable
internal fun BlurRadiusSliderItem(
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
) {
    var localValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }

    val steps = (SettingsDimensions.BlurRadiusMax - SettingsDimensions.BlurRadiusMin).roundToInt() - 1
    val sliderState =
        rememberSliderState(
            value = localValue,
            steps = steps,
            valueRange = SettingsDimensions.BlurRadiusMin..SettingsDimensions.BlurRadiusMax,
            onValueChangeFinished = { onValueChangeFinished(localValue.roundToInt().toFloat()) },
        )
    sliderState.onValueChange = { localValue = it.roundToInt().toFloat() }
    sliderState.value = localValue

    PreferenceEntry(
        title = { Text("${stringResource(R.string.blur_radius)} — ${localValue.roundToInt()} dp") },
        description = stringResource(R.string.blur_radius_desc),
        content = {
            Spacer(Modifier.height(4.dp))
            Slider(
                state = sliderState,
                modifier = Modifier.fillMaxWidth(),
                track = {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        trackCornerSize = 12.dp,
                    )
                },
            )
        },
    )
}

@Composable
internal fun HomeBackgroundSliderItem(
    title: String,
    value: Float,
    onValueChangeFinished: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
) {
    var localValue by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { localValue = value }

    val sliderState =
        rememberSliderState(
            value = localValue,
            steps = 19,
            valueRange = valueRange,
            onValueChangeFinished = { onValueChangeFinished(localValue) },
        )
    sliderState.onValueChange = { localValue = it }
    sliderState.value = localValue

    PreferenceEntry(
        title = { Text(title) },
        description = valueText(localValue),
        content = {
            Spacer(Modifier.height(4.dp))
            Slider(
                state = sliderState,
                modifier = Modifier.fillMaxWidth(),
                track = {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        trackCornerSize = 12.dp,
                    )
                },
            )
        },
    )
}
