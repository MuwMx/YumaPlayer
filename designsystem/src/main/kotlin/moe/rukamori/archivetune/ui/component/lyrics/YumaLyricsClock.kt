/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToLong

private const val DRIFT_SNAP_THRESHOLD_MS = 250L
private const val DRIFT_CORRECTION_FACTOR = 0.5f

@Composable
fun rememberYumaLyricsPositionMs(
    currentPositionProvider: () -> Long,
    isPlaying: Boolean,
    isWordSynced: Boolean,
    playbackSpeed: Float = 1f,
    syncOffsetMs: Int = 0,
    sessionKey: Any? = Unit,
    animationsDisabled: Boolean = false,
    timingTokens: TimingTokens = TimingTokens(),
): State<Long> {
    val leadMs = if (isWordSynced) timingTokens.richsyncOffsetMs else timingTokens.lineOffsetMs
    val latestPositionProvider = rememberUpdatedState(currentPositionProvider)
    val latestSpeed = rememberUpdatedState(playbackSpeed)

    val positionState = remember(sessionKey) {
        mutableLongStateOf(
            (currentPositionProvider().coerceAtLeast(0L) + syncOffsetMs + leadMs).coerceAtLeast(0L),
        )
    }

    LaunchedEffect(sessionKey, isPlaying, isWordSynced, playbackSpeed, syncOffsetMs, animationsDisabled, leadMs) {
        var anchorRawMs = latestPositionProvider.value().coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        var lastEffectiveMs = (anchorRawMs + syncOffsetMs + leadMs).coerceAtLeast(0L)
        positionState.longValue = lastEffectiveMs

        while (isActive) {
            val rawMs = latestPositionProvider.value().coerceAtLeast(0L)
            if (!isPlaying || animationsDisabled) {
                anchorRawMs = rawMs
                anchorFrameNanos = 0L
                val nextEffective = (rawMs + syncOffsetMs + leadMs).coerceAtLeast(0L)
                if (positionState.longValue != nextEffective) {
                    positionState.longValue = nextEffective
                }
                lastEffectiveMs = nextEffective
                delay(80L)
                continue
            }

            val frameNanos = withFrameNanos { it }
            if (anchorFrameNanos == 0L) {
                anchorFrameNanos = frameNanos
                anchorRawMs = rawMs
            }

            val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000f) * latestSpeed.value
            val projectedRawMs = anchorRawMs + elapsedMs.roundToLong()
            val driftMs = rawMs - projectedRawMs

            val smoothedRawMs =
                if (abs(driftMs) > DRIFT_SNAP_THRESHOLD_MS) {
                    anchorRawMs = rawMs
                    anchorFrameNanos = frameNanos
                    lastEffectiveMs = (rawMs + syncOffsetMs + leadMs).coerceAtLeast(0L)
                    rawMs
                } else {
                    val corrected = projectedRawMs + (driftMs * DRIFT_CORRECTION_FACTOR).roundToLong()
                    corrected.coerceAtLeast(lastEffectiveMs - syncOffsetMs - leadMs)
                }

            val nextEffective = (smoothedRawMs + syncOffsetMs + leadMs).coerceAtLeast(0L)
            lastEffectiveMs = nextEffective
            if (positionState.longValue != nextEffective) {
                positionState.longValue = nextEffective
            }
        }
    }

    return positionState
}
