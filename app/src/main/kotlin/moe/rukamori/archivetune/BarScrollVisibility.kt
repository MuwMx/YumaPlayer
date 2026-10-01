package moe.rukamori.archivetune

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class BarScrollVisibilityState(
    private val hideDistancePx: Float,
    private val idleTimeoutMs: Long = 800L,
    private val coroutineScope: CoroutineScope,
) {
    var scrollVisibilityFactor by mutableFloatStateOf(1f)
        private set

    private var accumulatedDownwardScroll = 0f
    private var idleJob: Job? = null

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (delta < 0f) {
                accumulatedDownwardScroll += -delta
                idleJob?.cancel()
                if (accumulatedDownwardScroll >= hideDistancePx) {
                    scrollVisibilityFactor = 0f
                }
                scheduleIdleReset()
            } else if (delta > 0f) {
                accumulatedDownwardScroll = 0f
                idleJob?.cancel()
                scrollVisibilityFactor = 1f
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (consumed.y > 0f || available.y > 0f) {
                accumulatedDownwardScroll = 0f
                idleJob?.cancel()
                scrollVisibilityFactor = 1f
            }
            return Offset.Zero
        }
    }

    private fun scheduleIdleReset() {
        idleJob = coroutineScope.launch {
            delay(idleTimeoutMs)
            accumulatedDownwardScroll = 0f
            scrollVisibilityFactor = 1f
        }
    }
}

@Composable
fun rememberBarScrollVisibility(
    hideDistance: Dp = 72.dp,
    idleTimeoutMs: Long = 800L,
): BarScrollVisibilityState {
    val density = LocalDensity.current
    val hideDistancePx = with(density) { hideDistance.toPx() }
    val scope = rememberCoroutineScope()
    return remember(hideDistancePx, idleTimeoutMs, scope) {
        BarScrollVisibilityState(
            hideDistancePx = hideDistancePx,
            idleTimeoutMs = idleTimeoutMs,
            coroutineScope = scope,
        )
    }
}
