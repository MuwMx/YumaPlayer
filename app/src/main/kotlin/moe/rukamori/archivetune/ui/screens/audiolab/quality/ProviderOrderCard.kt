/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.quality

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import kotlin.math.roundToInt

private val ArrowGap = 24.dp

@Immutable
internal data class ProviderItemUi(
    val id: String,
    val name: String,
    val status: String,
    val active: Boolean,
)

@Composable
internal fun ProviderOrderCard(
    order: List<String>,
    providerUi: (String) -> ProviderItemUi,
    fallbackItem: ProviderItemUi,
    onReorder: (List<String>) -> Unit,
    onTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val rowHeight = 68.dp + 26.dp * (density.fontScale - 1f).coerceAtLeast(0f)
    val slotPx = with(density) { (rowHeight + ArrowGap).toPx() }
    var items by remember { mutableStateOf(order) }
    var dragging by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(order) { if (dragging == null) items = order }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val totalCount = items.size + 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .yumaGlassCard(shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge))
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * totalCount + ArrowGap * (totalCount - 1).coerceAtLeast(0)),
        ) {
            val chevronColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            for (i in 0 until totalCount - 1) {
                Canvas(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = rowHeight * (i + 1) + ArrowGap * i)
                        .size(width = 24.dp, height = ArrowGap),
                ) {
                    val stroke = 2.dp.toPx()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val half = 7.dp.toPx()
                    drawLine(chevronColor, Offset(cx - half, cy - half / 2f), Offset(cx, cy + half / 2f), stroke, StrokeCap.Round)
                    drawLine(chevronColor, Offset(cx + half, cy - half / 2f), Offset(cx, cy + half / 2f), stroke, StrokeCap.Round)
                }
            }

            items.forEachIndexed { index, id ->
                key(id) {
                    val isDragged = dragging == id
                    val y = remember { Animatable(index * slotPx) }
                    val held = remember { floatArrayOf(index * slotPx) }
                    LaunchedEffect(isDragged, index, animationsDisabled) {
                        if (!isDragged) {
                            y.snapTo(held[0])
                            if (animationsDisabled) y.snapTo(index * slotPx)
                            else y.animateTo(index * slotPx, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    }
                    val lift by animateFloatAsState(
                        targetValue = if (isDragged) 1f else 0f,
                        animationSpec = if (animationsDisabled) snap() else spring(dampingRatio = 0.7f, stiffness = 600f),
                        label = "providerLift",
                    )
                    ProviderRow(
                        ui = providerUi(id),
                        rowHeight = rowHeight,
                        isDragged = isDragged,
                        draggable = true,
                        onTap = { onTap(id) },
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .offset {
                                if (isDragged) held[0] = index * slotPx + dragOffset
                                IntOffset(0, (if (isDragged) held[0] else y.value).roundToInt())
                            }
                            .graphicsLayer { scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift },
                        handleModifier = Modifier
                            .size(48.dp)
                            .pointerInput(Unit) { detectTapGestures {} }
                            .pointerInput(id) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = id; dragOffset = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val list = currentItems
                                        val from = list.indexOf(id)
                                        val to = (from + (dragOffset / slotPx).roundToInt()).coerceIn(0, list.size - 1)
                                        if (to != from) {
                                            items = list.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffset -= (to - from) * slotPx
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { dragging = null; dragOffset = 0f; currentOnReorder(currentItems) },
                                    onDragCancel = { dragging = null; dragOffset = 0f; currentOnReorder(currentItems) },
                                )
                            },
                    )
                }
            }

            ProviderRow(
                ui = fallbackItem,
                rowHeight = rowHeight,
                isDragged = false,
                draggable = false,
                onTap = null,
                modifier = Modifier.offset { IntOffset(0, (items.size * slotPx).roundToInt()) },
            )
        }
    }
}

@Composable
private fun ProviderRow(
    ui: ProviderItemUi,
    rowHeight: Dp,
    isDragged: Boolean,
    draggable: Boolean,
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val rowBg = if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow
    val dotColor = if (ui.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val titleColor = if (ui.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(SettingsDimensions.GlassCornerRadius))
            .background(rowBg)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(start = 16.dp, end = if (draggable) 6.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(SettingsDimensions.SegmentedBadgeSize).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(ui.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ui.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (draggable) {
            Canvas(modifier = handleModifier) {
                val w = 20.dp.toPx()
                val x0 = (size.width - w) / 2f
                val cy = size.height / 2f
                val gap = 4.dp.toPx()
                val stroke = 2.dp.toPx()
                drawLine(handleColor, Offset(x0, cy - gap), Offset(x0 + w, cy - gap), stroke, StrokeCap.Round)
                drawLine(handleColor, Offset(x0, cy), Offset(x0 + w, cy), stroke, StrokeCap.Round)
                drawLine(handleColor, Offset(x0, cy + gap), Offset(x0 + w, cy + gap), stroke, StrokeCap.Round)
            }
        }
    }
}
