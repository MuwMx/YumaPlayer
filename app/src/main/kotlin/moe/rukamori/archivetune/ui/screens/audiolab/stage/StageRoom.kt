/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab.stage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.audiodsp.StageSettings
import moe.rukamori.archivetune.audiodsp.StageSource
import moe.rukamori.archivetune.ui.settings.SettingsDimensions
import moe.rukamori.archivetune.ui.theme.yumaClickable
import moe.rukamori.archivetune.ui.theme.yumaGlassCard
import kotlin.math.hypot
import kotlin.math.sqrt

private class LastStageFrame {
    var view: StageView? = null
}

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

    val yawState = remember { mutableFloatStateOf(DefaultCameraYaw) }
    val pitchState = remember { mutableFloatStateOf(DefaultCameraPitch) }
    val frameTick = remember { mutableFloatStateOf(0f) }
    val smoothedLevels = remember { FloatArray(6) }
    val lastFrame = remember { LastStageFrame() }

    val on by animateFloatAsState(
        targetValue = if (stage.enabled) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else tween(400),
        label = "stageOn",
    )
    val labels = rememberStageLabels(stage)

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
                    val target = sqrt(raw.getOrElse(i) { 0f }.coerceIn(0f, 1f))
                    val cur = smoothedLevels[i]
                    smoothedLevels[i] += (target - cur) * if (target > cur) 0.45f else 0.12f
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
                        val view = lastFrame.view ?: return@awaitEachGesture
                        val st = currentStage
                        val hit = hitTest(view, st, down.position, hitThresholdPx)
                        if (hit != null) currentOnSelect(hit)

                        val lifted = hit ?: currentSelected
                        var dragging = false
                        var lifting = false
                        var grab: Pair<Float, Float>? = null
                        var lastCentroid = Offset.Zero
                        var liftX = 0f
                        var liftY = 0f
                        var liftZ = 0f
                        var liftInit = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val centroid = pressed.fold(Offset.Zero) { sum, c -> sum + c.position } / pressed.size.toFloat()
                                if (!lifting) {
                                    lifting = true
                                    lastCentroid = centroid
                                    val spot = spotOf(currentStage, lifted)
                                    if (spot != null) {
                                        liftX = spot.first
                                        liftY = spot.second
                                        liftZ = spot.third
                                        liftInit = true
                                    }
                                }
                                if (liftInit) {
                                    val pxPerUnit = view.heightPixels(liftX, liftY, liftZ)
                                    liftY = (liftY - (centroid.y - lastCentroid.y) / pxPerUnit).coerceIn(0f, 1f)
                                    if (lifted == ListenerKey) {
                                        currentOnMoveListener(liftX, liftY, liftZ)
                                    } else {
                                        currentOnMoveObject(lifted, liftX, liftY, liftZ)
                                    }
                                }
                                lastCentroid = centroid
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            if (lifting) {
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = pressed.first()
                            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                            }
                            if (!dragging) continue

                            val spot = hit?.let { spotOf(currentStage, it) }
                            if (hit != null && spot != null) {
                                val now = view.onLevel(change.position.x, change.position.y, spot.second)
                                if (now != null) {
                                    val offset = grab
                                        ?: (view.onLevel(down.position.x, down.position.y, spot.second)
                                            ?.let { (spot.first - it.first) to (spot.third - it.second) }
                                            ?: (0f to 0f)).also { grab = it }
                                    val nx = (now.first + offset.first).coerceIn(-1f, 1f)
                                    val nz = (now.second + offset.second).coerceIn(-1f, 1f)
                                    if (hit == ListenerKey) {
                                        currentOnMoveListener(nx, spot.second, nz)
                                    } else {
                                        currentOnMoveObject(hit, nx, spot.second, nz)
                                    }
                                }
                            } else {
                                val delta = change.positionChange()
                                yawState.floatValue = (yawState.floatValue - delta.x * 0.35f).let {
                                    if (it > 180f) it - 360f else if (it < -180f) it + 360f else it
                                }
                                pitchState.floatValue = (pitchState.floatValue + delta.y * 0.3f).coerceIn(4f, 88f)
                            }
                            change.consume()
                        }

                        if (!dragging && !lifting && hit == null) {
                            if (down.uptimeMillis - lastEmptyTapMs < 320L) {
                                yawState.floatValue = DefaultCameraYaw
                                pitchState.floatValue = DefaultCameraPitch
                                lastEmptyTapMs = 0L
                            } else {
                                lastEmptyTapMs = down.uptimeMillis
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
            lastFrame.view = view
            drawStageRoom(
                view = view,
                stage = stage,
                selectedKey = selectedKey,
                levels = smoothedLevels,
                palette = palette,
                on = on,
                labels = labels,
                compact = false,
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

internal fun labelsOf(
    objects: List<Pair<Int, StageSource>>,
    names: Map<StageSource, String>,
): Map<Int, String> {
    val seen = IntArray(StageSource.entries.size)
    return objects.associate { (id, source) ->
        val n = ++seen[source.ordinal]
        val base = names.getValue(source)
        id to if (n == 1) base else "$base $n"
    }
}

@Composable
private fun rememberStageLabels(stage: StageSettings): Map<Int, TextLayoutResult> {
    val names = mapOf(
        StageSource.Vocal to stringResource(R.string.stage_source_vocal),
        StageSource.Center to stringResource(R.string.stage_source_center),
        StageSource.Left to stringResource(R.string.stage_source_left),
        StageSource.Right to stringResource(R.string.stage_source_right),
        StageSource.Ambience to stringResource(R.string.stage_source_ambience),
        StageSource.Sub to stringResource(R.string.stage_source_sub),
    )
    val measurer = rememberTextMeasurer()
    val keys = remember(stage.objects) { stage.objects.map { it.id to it.source } }
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    return remember(keys, names, style) {
        labelsOf(keys, names).mapValues { (_, text) -> measurer.measure(text, style) }
    }
}

private fun spotOf(settings: StageSettings, key: Int): Triple<Float, Float, Float>? =
    if (key == ListenerKey) {
        settings.listener.let { Triple(it.x, it.y, it.z) }
    } else {
        settings.objects.firstOrNull { it.id == key }?.let { Triple(it.x, it.y, it.z) }
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
