/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.AudioLabSoundProfiles
import moe.rukamori.archivetune.audiodsp.AudioLabState
import moe.rukamori.archivetune.audiodsp.SpectrumAnalyzer
import moe.rukamori.archivetune.ui.settings.SettingsAnimations
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.LocalYumaColors
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import kotlin.math.pow

private const val SpectrumBandCount = 28
private val HeroSpectrumBands = DoubleArray(SpectrumBandCount) { index ->
    32.0 * (16_000.0 / 32.0).pow(index / (SpectrumBandCount - 1).toDouble())
}

private data class SignalChainNode(
    val id: String,
    @StringRes val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
fun AudioLabSpectrumHero(
    state: AudioLabState,
    bypassed: Boolean,
    isPlaying: Boolean,
    onToggleBypass: () -> Unit,
    onNavigateEq: () -> Unit,
    onNavigateStage: () -> Unit,
    onNavigateFx: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val analyzer = remember { SpectrumAnalyzer(bands = HeroSpectrumBands) }
    val frameTick = remember { mutableFloatStateOf(0f) }
    val lastTick = remember { floatArrayOf(-1f) }

    val shouldAnimate = isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) {
            frameTick.floatValue = 0f
            lastTick[0] = -1f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                frameTick.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    val activeNodes = remember(state, onNavigateEq, onNavigateStage, onNavigateFx) {
        buildList {
            if (state.eq.enabled) add(SignalChainNode("eq", R.string.audio_lab_eq_title, onNavigateEq))
            if (state.stage.enabled) add(SignalChainNode("stage", R.string.stage_title, onNavigateStage))
            if (state.reverb.enabled) add(SignalChainNode("reverb", R.string.audio_lab_reverb_title, onNavigateFx))
            if (state.eightD.enabled) add(SignalChainNode("eight_d", R.string.audio_lab_eight_d_title, onNavigateFx))
            if (state.loudness.enabled) add(SignalChainNode("loudness", R.string.audio_lab_loudness_title, onNavigateFx))
            if (state.spatial.enabled) add(SignalChainNode("spatial", R.string.audio_lab_spatial_title, onNavigateFx))
        }
    }
    val hasActiveChain = activeNodes.isNotEmpty()

    val shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .yumaGlassCard(shape = shape)
            .clip(shape)
            .padding(vertical = SettingsDimensions.SegmentedItemPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor by animateColorAsState(
                    targetValue = when {
                        bypassed -> MaterialTheme.colorScheme.error
                        hasActiveChain -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    animationSpec = if (animationsDisabled) snap() else tween(240),
                    label = "dspDotColor",
                )
                Box(
                    modifier = Modifier
                        .size(SettingsDimensions.BadgeSize)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.audio_lab_dsp_engine),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val bypassShape = RoundedCornerShape(50)
            val bypassBg by animateColorAsState(
                targetValue = if (bypassed) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
                },
                animationSpec = if (animationsDisabled) snap() else tween(200),
                label = "bypassBg",
            )
            val bypassFg = if (bypassed) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .clip(bypassShape)
                    .yumaClickable(onClick = onToggleBypass)
                    .background(bypassBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(
                        if (bypassed) R.string.audio_lab_bypass_active else R.string.audio_lab_bypass,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = bypassFg,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal),
        ) {
            val tick = frameTick.floatValue
            if (shouldAnimate && tick != lastTick[0]) {
                analyzer.update()
                lastTick[0] = tick
            }

            val usableWidth = size.width.coerceAtLeast(1f)
            val maxBarHeight = size.height.coerceAtLeast(1f)
            val slotWidth = usableWidth / SpectrumBandCount
            val barWidth = (slotWidth * 0.56f).coerceAtLeast(2.dp.toPx())
            val corner = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
            val activeBrush = if (shouldAnimate && !bypassed) {
                Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.88f),
                        primary.copy(alpha = 0.22f),
                    ),
                    startY = 0f,
                    endY = size.height,
                )
            } else {
                null
            }
            val mutedColor = if (bypassed) {
                onSurfaceVariant.copy(alpha = 0.20f)
            } else {
                primary.copy(alpha = 0.18f)
            }

            val levels = analyzer.levels
            for (i in 0 until SpectrumBandCount) {
                val normalized = if (shouldAnimate && !bypassed) {
                    ((levels[i] + 66f) / 60f).coerceIn(0.06f, 1f)
                } else {
                    0.06f
                }
                val barHeight = (maxBarHeight * normalized).coerceAtLeast(barWidth)
                val x = i * slotWidth + (slotWidth - barWidth) * 0.5f
                val y = size.height - barHeight
                if (activeBrush != null) {
                    drawRoundRect(
                        brush = activeBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner,
                    )
                } else {
                    drawRoundRect(
                        color = mutedColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = corner,
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SettingsDimensions.SegmentedItemPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (activeNodes.isEmpty()) {
                item(key = "idle") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.audio_lab_chain_idle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(items = activeNodes, key = { it.id }) { node ->
                    val nodeBg = if (bypassed) {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                    }
                    val nodeFg = if (bypassed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .yumaClickable(onClick = node.onClick)
                            .background(nodeBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = stringResource(node.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = nodeFg,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioLabSoundProfilesRow(
    activeProfileId: String?,
    bypassed: Boolean,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val yumaColors = LocalYumaColors.current
    val chipShape = RoundedCornerShape(50)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.ScreenHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = AudioLabSoundProfiles.All, key = { it.id }) { profile ->
            val selected = !bypassed && profile.id == activeProfileId
            val bg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    yumaColors.cardBackgroundOpaque
                },
                animationSpec = if (animationsDisabled) snap() else tween(200),
                label = "profileChipBg",
            )
            val fg = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }

            Box(
                modifier = Modifier
                    .clip(chipShape)
                    .yumaClickable { onSelectProfile(profile.id) }
                    .background(bg)
                    .border(1.dp, borderColor, chipShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = stringResource(soundProfileLabelRes(profile.id)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = fg,
                )
            }
        }
    }
}

@StringRes
private fun soundProfileLabelRes(profileId: String): Int = when (profileId) {
    AudioLabSoundProfiles.Pure.id -> R.string.audio_lab_profile_pure
    AudioLabSoundProfiles.Studio3D.id -> R.string.audio_lab_profile_studio_3d
    AudioLabSoundProfiles.ConcertHall.id -> R.string.audio_lab_profile_concert_hall
    AudioLabSoundProfiles.LateNight.id -> R.string.audio_lab_profile_late_night
    AudioLabSoundProfiles.ClubBass.id -> R.string.audio_lab_profile_club_bass
    else -> R.string.audio_lab_profile_pure
}
