/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabEqSettings
import moe.rukamori.archivetune.audiodsp.ResponseCurve
import moe.rukamori.archivetune.audiodsp.StandardEqFrequencies
import moe.rukamori.archivetune.ui.main.LocalPlayerConnection
import moe.rukamori.archivetune.ui.screens.settings.components.YumaSettingsScaffold
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import moe.rukamori.archivetune.viewmodels.AudioLabViewModel
import kotlin.math.ln

private val GridFrequencies = doubleArrayOf(60.0, 250.0, 1000.0, 4000.0, 16000.0)

@Composable
fun AudioLabEqualizerScreen(
    navController: NavController,
    viewModel: AudioLabViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying = playerConnection?.isPlaying?.collectAsStateWithLifecycle()?.value ?: false

    ListenToAudioTap(active = isPlaying)

    YumaSettingsScaffold(
        title = { Text(stringResource(R.string.audio_lab_eq_title)) },
        onBackClick = { navController.navigateUp() },
    ) {
        EqResponseCurveCard(
            eq = state.eq,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        )

        Spacer(Modifier.height(SettingsDimensions.SectionSpacing))

        AudioLabEqSection(
            eq = state.eq,
            onEnabledChange = viewModel::setEqEnabled,
            onPresetSelected = viewModel::applyEqPreset,
            onPreampChange = viewModel::setEqPreamp,
            onBandGainChange = viewModel::setEqBandGain,
            onReset = viewModel::resetEq,
        )
    }
}

@Composable
private fun EqResponseCurveCard(
    eq: AudioLabEqSettings,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    val curve = remember { ResponseCurve(ResponseCurve.logSpaced(128)) }
    val magnitudes = remember(eq) {
        val parametric = eq.toParametricEq()
        curve.response(parametric.bands)
    }

    Box(
        modifier = modifier
            .height(160.dp)
            .yumaGlassCard(shape = shape)
            .clip(shape),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val padH = 14.dp.toPx()
            val padV = 16.dp.toPx()
            val plotW = (size.width - padH * 2f).coerceAtLeast(1f)
            val plotH = (size.height - padV * 2f).coerceAtLeast(1f)
            val midY = padV + plotH * 0.5f

            val minLog = ln(20.0)
            val maxLog = ln(20_000.0)
            fun xForFreq(freq: Double): Float {
                val t = ((ln(freq.coerceIn(20.0, 20_000.0)) - minLog) / (maxLog - minLog)).toFloat()
                return padH + t * plotW
            }

            fun yForDb(db: Float): Float {
                val norm = (db / 15f).coerceIn(-1f, 1f)
                return midY - norm * (plotH * 0.5f)
            }

            val gridColor = onSurfaceVariant.copy(alpha = 0.16f)
            GridFrequencies.forEach { freq ->
                val x = xForFreq(freq)
                drawLine(gridColor, Offset(x, padV), Offset(x, padV + plotH), 1.dp.toPx())
            }
            drawLine(
                color = onSurfaceVariant.copy(alpha = 0.28f),
                start = Offset(padH, midY),
                end = Offset(padH + plotW, midY),
                strokeWidth = 1.dp.toPx(),
            )

            val strokeColor = if (eq.enabled) primary else onSurfaceVariant.copy(alpha = 0.45f)
            val linePath = Path()
            val fillPath = Path()

            curve.frequencies.forEachIndexed { index, freq ->
                val x = xForFreq(freq)
                val db = if (eq.enabled) magnitudes[index].toFloat() else 0f
                val y = yForDb(db)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, midY)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(xForFreq(curve.frequencies.last()), midY)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(strokeColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = padV,
                    endY = padV + plotH,
                ),
            )
            drawPath(
                path = linePath,
                color = strokeColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val dotRadius = 3.5.dp.toPx()
            StandardEqFrequencies.forEachIndexed { index, freq ->
                val gain = if (eq.enabled) eq.bandGainsDb.getOrElse(index) { 0f } else 0f
                val center = Offset(xForFreq(freq.toDouble()), yForDb(gain))
                drawCircle(color = surface, radius = dotRadius + 1.2.dp.toPx(), center = center)
                drawCircle(color = strokeColor, radius = dotRadius, center = center)
            }
        }
    }
}
