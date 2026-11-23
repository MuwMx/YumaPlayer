/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.transitions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.TransitionMode
import moe.rukamori.archivetune.ui.component.MarqueeText
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import kotlin.math.roundToInt

private val GridModes = listOf(
    TransitionMode.CROSSFADE,
    TransitionMode.AUTOMIX,
    TransitionMode.CROSSMIX,
)
private val CardGap = 10.dp
private val SmallCardHeight = 136.dp
private val BigStageHeight = 144.dp
private val MinStageHeight = 44.dp
private val CardPadding = 14.dp
private val CardShape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)
private val StageShape = RoundedCornerShape(16.dp)
private val CardSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 360f)

private class CardMotion(
    val x: State<Float>,
    val width: State<Float>,
    val row: State<Float>,
    val opened: State<Float>,
)

@Composable
internal fun TransitionModeGrid(
    selected: TransitionMode,
    modifier: Modifier = Modifier,
    card: @Composable (mode: TransitionMode, opened: State<Float>) -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val spec = if (animationsDisabled) snap() else CardSpring
    val wide = if (selected in GridModes) selected else TransitionMode.CROSSMIX
    val side = GridModes.filter { it != wide }
    val motions = GridModes.map { mode ->
        val isWide = mode == wide
        CardMotion(
            x = animateFloatAsState(if (isWide) 0f else side.indexOf(mode) * 0.5f, spec, label = "cardX"),
            width = animateFloatAsState(if (isWide) 1f else 0.5f, spec, label = "cardWidth"),
            row = animateFloatAsState(if (isWide) 1f else 0f, spec, label = "cardRow"),
            opened = animateFloatAsState(if (mode == selected) 1f else 0f, spec, label = "cardOpen"),
        )
    }

    Layout(
        content = { GridModes.forEachIndexed { i, mode -> card(mode, motions[i].opened) } },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val total = constraints.maxWidth
        val gap = CardGap.roundToPx()
        val rowStep = (SmallCardHeight + CardGap).toPx()
        val placeables = measurables.mapIndexed { i, measurable ->
            val width = (motions[i].width.value * (total + gap) - gap).roundToInt().coerceIn(1, total)
            measurable.measure(Constraints.fixedWidth(width))
        }
        val tops = motions.map { (it.row.value * rowStep).roundToInt() }
        val height = placeables.indices.maxOf { tops[it] + placeables[it].height }
        layout(total, height) {
            placeables.forEachIndexed { i, placeable ->
                placeable.place(
                    x = (motions[i].x.value * (total + gap)).roundToInt(),
                    y = tops[i],
                    zIndex = if (GridModes[i] == wide) 1f else 0f,
                )
            }
        }
    }
}

@Composable
internal fun TransitionModeCard(
    title: String,
    selected: Boolean,
    opened: State<Float>,
    onSelect: () -> Unit,
    picture: @Composable () -> Unit,
    options: @Composable ColumnScope.() -> Unit,
) {
    val selectedLabel = stringResource(R.string.transition_selected).uppercase()
    val stageBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.32f)

    Layout(
        contents = listOf(
            { CardHeader(title = title, selectedLabel = selectedLabel, active = selected, opened = opened) },
            {
                Box(
                    modifier = Modifier
                        .clip(StageShape)
                        .background(stageBg),
                ) {
                    picture()
                }
            },
            {
                Column {
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(content = options)
                    }
                }
            },
        ),
        modifier = Modifier
            .semantics {
                this.selected = selected
                this.role = Role.RadioButton
            }
            .yumaClickable(enabled = !selected, onClick = onSelect)
            .yumaGlassCard(shape = CardShape)
            .clip(CardShape),
    ) { (header, stage, extras), constraints ->
        val pad = CardPadding.roundToPx()
        val space = 10.dp.roundToPx()
        val inner = (constraints.maxWidth - pad * 2).coerceAtLeast(0)
        val headerPlaceable = header.first().measure(Constraints(maxWidth = inner))
        val smallStage = (SmallCardHeight.roundToPx() - pad * 2 - headerPlaceable.height - space)
            .coerceAtLeast(MinStageHeight.roundToPx())
        val open = opened.value
        val stageHeight = (smallStage + (BigStageHeight.roundToPx() - smallStage) * open)
            .roundToInt()
            .coerceAtLeast(1)
        val stagePlaceable = stage.first().measure(Constraints.fixed(inner, stageHeight))
        val extrasPlaceable = extras.firstOrNull()?.measure(Constraints(maxWidth = inner))
        val stageTop = pad + headerPlaceable.height + space
        val height = stageTop + stageHeight + (extrasPlaceable?.height ?: 0) + pad
        layout(constraints.maxWidth, height) {
            headerPlaceable.place(pad, pad)
            stagePlaceable.place(pad, stageTop)
            extrasPlaceable?.place(pad, stageTop + stageHeight)
        }
    }
}

@Composable
private fun CardHeader(
    title: String,
    selectedLabel: String,
    active: Boolean,
    opened: State<Float>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    ) {
        MarqueeText(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = opened.value.coerceIn(0f, 1f) },
        )
    }
}
