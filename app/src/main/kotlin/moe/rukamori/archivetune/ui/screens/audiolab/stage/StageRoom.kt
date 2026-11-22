/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import kotlin.math.hypot

@Composable
internal fun StageRoom(
    stage: StageSettings,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
    onMoveObject: (id: Int, x: Float, y: Float, z: Float) -> Unit,
    onMoveListener: (x: Float, y: Float, z: Float) -> Unit,
    onOpenAddSheet: () -> Unit,
    stageLevelsProvider: () -> FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val palette = rememberStageCanvasPalette()
    val density = LocalDensity.current
    val hitThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    val tapSlopPx = remember(density) { with(density) { 8.dp.toPx() } }

    val yawState = remember { mutableFloatStateOf(DefaultCameraYaw) }
    val pitchState = remember { mutableFloatStateOf(DefaultCameraPitch) }
    val frameTick = remember { mutableFloatStateOf(0f) }
    val smoothedLevels = remember { FloatArray(6) }

    val currentStage by rememberUpdatedState(stage)
    val currentSelected by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMoveObject by rememberUpdatedState(onMoveObject)
    val currentOnMoveListener by rememberUpdatedState(onMoveListener)

    val shouldAnimateMeters = stage.enabled && isPlaying && !animationsDisabled
    LaunchedEffect(shouldAnimateMeters) {
        if (!shouldAnimateMeters) {
            smoothedLevels.fill(0f)
            frameTick.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val raw = stageLevelsProvider()
                for (i in smoothedLevels.indices) {
                    val target = raw.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                    val cur = smoothedLevels[i]
                    smoothedLevels[i] = if (target > cur) {
                        cur + (target - cur) * 0.45f
                    } else {
                        (cur * 0.88f).coerceAtLeast(target)
                    }
                }
                frameTick.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    val shape = RoundedCornerShape(SettingsDimensions.SegmentedCornerLarge)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .yumaGlassCard(shape = shape)
            .clip(shape),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 260.dp)
                .pointerInput(Unit) {
                    var lastEmptyTapMs = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (size.width <= 0 || size.height <= 0) return@awaitEachGesture

                        val st = currentStage
                        val view = StageView(
                            size = IntSize(size.width, size.height),
                            box = st.box,
                            yawDeg = yawState.floatValue,
                            pitchDeg = pitchState.floatValue,
                        )

                        val hit = hitTest(view, st, down.position, hitThresholdPx)
                        if (hit != null) {
                            currentOnSelect(hit)
                            var curX = if (hit == ListenerKey) st.listener.x else st.objects.firstOrNull { it.id == hit }?.x ?: 0f
                            var curY = if (hit == ListenerKey) st.listener.y else st.objects.firstOrNull { it.id == hit }?.y ?: 0.4f
                            var curZ = if (hit == ListenerKey) st.listener.z else st.objects.firstOrNull { it.id == hit }?.z ?: 0f
                            val depth = view.projectNormalized(curX, curY, curZ).depth
                            val (startPlaneX, startPlaneZ) = view.onLevel(down.position.x, down.position.y, curY)
                            val offsetX = curX - startPlaneX
                            val offsetZ = curZ - startPlaneZ

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    val avgDy = pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() }.toFloat() / pressed.size
                                    val pxPerUnit = view.heightPixels(depth).coerceAtLeast(1f)
                                    curY = (curY - avgDy / pxPerUnit).coerceIn(0f, 1f)
                                } else {
                                    val p = pressed.first()
                                    val (nx, nz) = view.onLevel(p.position.x, p.position.y, curY)
                                    curX = (nx + offsetX).coerceIn(-1f, 1f)
                                    curZ = (nz + offsetZ).coerceIn(-1f, 1f)
                                }
                                pressed.forEach { it.consume() }
                                if (hit == ListenerKey) {
                                    currentOnMoveListener(curX, curY, curZ)
                                } else {
                                    currentOnMoveObject(hit, curX, curY, curZ)
                                }
                            }
                        } else {
                            var totalDrag = 0f
                            var liftY = if (currentSelected == ListenerKey) {
                                st.listener.y
                            } else {
                                st.objects.firstOrNull { it.id == currentSelected }?.y ?: 0.4f
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break

                                if (pressed.size >= 2) {
                                    val avgDy = pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() }.toFloat() / pressed.size
                                    totalDrag += kotlin.math.abs(avgDy)
                                    val targetId = currentSelected
                                    if (targetId == ListenerKey) {
                                        val pxPerUnit = view.heightPixels(view.projectNormalized(st.listener.x, liftY, st.listener.z).depth).coerceAtLeast(1f)
                                        liftY = (liftY - avgDy / pxPerUnit).coerceIn(0f, 1f)
                                        currentOnMoveListener(st.listener.x, liftY, st.listener.z)
                                    } else {
                                        val obj = st.objects.firstOrNull { it.id == targetId }
                                        if (obj != null) {
                                            val pxPerUnit = view.heightPixels(view.projectNormalized(obj.x, liftY, obj.z).depth).coerceAtLeast(1f)
                                            liftY = (liftY - avgDy / pxPerUnit).coerceIn(0f, 1f)
                                            currentOnMoveObject(obj.id, obj.x, liftY, obj.z)
                                        }
                                    }
                                    pressed.forEach { it.consume() }
                                } else {
                                    val p = pressed.first()
                                    val dx = p.position.x - p.previousPosition.x
                                    val dy = p.position.y - p.previousPosition.y
                                    totalDrag += hypot(dx, dy)
                                    yawState.floatValue += dx * 0.45f
                                    pitchState.floatValue = (pitchState.floatValue + dy * 0.35f).coerceIn(4f, 88f)
                                    p.consume()
                                }
                            }
                            if (totalDrag < tapSlopPx) {
                                val now = System.currentTimeMillis()
                                if (now - lastEmptyTapMs < 320L) {
                                    yawState.floatValue = DefaultCameraYaw
                                    pitchState.floatValue = DefaultCameraPitch
                                    lastEmptyTapMs = 0L
                                } else {
                                    lastEmptyTapMs = now
                                }
                            }
                        }
                    }
                },
        ) {
            frameTick.floatValue
            val intSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
            val view = StageView(
                size = intSize,
                box = stage.box,
                yawDeg = yawState.floatValue,
                pitchDeg = pitchState.floatValue,
            )
            drawStageRoom(
                view = view,
                stage = stage,
                selectedKey = selectedKey,
                levels = smoothedLevels,
                palette = palette,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(SettingsDimensions.SectionSpacing),
            horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.BadgePaddingH),
        ) {
            StageOverlayButton(
                iconRes = R.drawable.replay,
                contentDescription = stringResource(R.string.stage_action_reset),
                onClick = {
                    yawState.floatValue = DefaultCameraYaw
                    pitchState.floatValue = DefaultCameraPitch
                },
            )
            StageOverlayButton(
                iconRes = R.drawable.add,
                contentDescription = stringResource(R.string.stage_action_add),
                onClick = onOpenAddSheet,
            )
        }

        Text(
            text = stringResource(R.string.stage_gesture_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    horizontal = SettingsDimensions.ScreenHorizontalPadding,
                    vertical = SettingsDimensions.RowVerticalPadding,
                ),
        )
    }
}

@Composable
private fun StageOverlayButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .yumaClickable(onClick = onClick)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
        )
    }
}

private fun hitTest(
    view: StageView,
    stage: StageSettings,
    position: Offset,
    thresholdPx: Float,
): Int? {
    var bestKey: Int? = null
    var bestDist = thresholdPx

    val lp = view.projectNormalized(stage.listener.x, stage.listener.y, stage.listener.z)
    val ld = hypot(position.x - lp.x, position.y - lp.y)
    if (ld < bestDist) {
        bestDist = ld
        bestKey = ListenerKey
    }

    for (obj in stage.objects) {
        val op = view.projectNormalized(obj.x, obj.y, obj.z)
        val od = hypot(position.x - op.x, position.y - op.y)
        if (od < bestDist) {
            bestDist = od
            bestKey = obj.id
        }
    }
    return bestKey
}
