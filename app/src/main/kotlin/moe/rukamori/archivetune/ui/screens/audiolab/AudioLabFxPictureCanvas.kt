/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.audiolab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.audiodsp.AudioTap

private class LevelFollower {
    private val scratch = FloatArray(512)
    private var lastTime = -1f
    var dt = 0f
        private set
    var level = 0f
        private set
    var punch = 0f
        private set

    fun tick(t: Float, isPlaying: Boolean) {
        if (t == lastTime) {
            dt = 0f
            return
        }
        dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.05f)
        lastTime = t

        val raw = if (isPlaying && AudioTap.isLive) AudioTap.level(scratch) else 0f
        val rate = if (raw > level) 0.5f else 0.12f
        level += (raw - level) * rate
        val targetPunch = ((raw - level) * 3.5f + level * 1.4f).coerceIn(0f, 1f)
        val punchRate = if (targetPunch > punch) 0.5f else 0.12f
        punch += (targetPunch - punch) * punchRate
    }
}

@Composable
internal fun FxPictureCanvas(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onDraw: DrawScope.(t: Float, level: Float, punch: Float, dt: Float) -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val time = remember { mutableFloatStateOf(0f) }
    val follower = remember { LevelFollower() }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            time.floatValue = 0f
            return@LaunchedEffect
        }
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time.floatValue = (now - startNanos) / 1_000_000_000f
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = time.floatValue
        follower.tick(t, isPlaying && !animationsDisabled)
        onDraw(t, follower.level, follower.punch, follower.dt)
    }
}
