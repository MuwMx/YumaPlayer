package moe.rukamori.archivetune.ui.player.player_0

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints

/**
 * Custom single-pass Layout for the full-screen player.
 *
 * Slot order: toolbar (0), cover (1), controls (2).
 */
@Composable
fun PlayerLayout(
    modifier: Modifier = Modifier,
    toolbar: @Composable () -> Unit,
    cover: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    Layout(
        content = {
            toolbar()
            cover()
            controls()
        },
        modifier = modifier,
        measurePolicy = MeasurePolicy { measurables, constraints ->
            check(measurables.size == 3) {
                "PlayerLayout expects exactly 3 root children: toolbar, cover, controls"
            }

            val toolbarMeasurable  = measurables[0]
            val coverMeasurable    = measurables[1]
            val controlsMeasurable = measurables[2]

            val maxW = constraints.maxWidth
            val maxH = constraints.maxHeight

            // ── 1. Measure toolbar (allow widthIn inside child to work) ────────
            val toolbarPlaceable = toolbarMeasurable.measure(
                Constraints(
                    minWidth  = 0,
                    maxWidth  = maxW,
                    minHeight = 0,
                    maxHeight = maxH
                )
            )
            val toolbarH = toolbarPlaceable.height

            // ── 2. Measure controls (allow widthIn inside child to work) ───────
            val controlsPlaceable = controlsMeasurable.measure(
                Constraints(
                    minWidth  = 0,
                    maxWidth  = maxW,
                    minHeight = 0,
                    maxHeight = (maxH - toolbarH).coerceAtLeast(0)
                )
            )
            val controlsH = controlsPlaceable.height

            // ── 3. Space left for the cover ───────────────────────────────────
            val availableCoverH = (maxH - toolbarH - controlsH).coerceAtLeast(0)

            // ── 4. Measure cover — square, constrained to available space ─────
            val coverSide = minOf(maxW, availableCoverH)
            val coverPlaceable = coverMeasurable.measure(
                Constraints.fixed(coverSide, coverSide)
            )
            val coverH = coverPlaceable.height

            // ── 5. Place (all components centered horizontally) ───────────────
            val toolbarX  = (maxW - toolbarPlaceable.width) / 2
            val coverX    = (maxW - coverPlaceable.width) / 2
            val coverY    = toolbarH + (availableCoverH - coverH) / 2
            val controlsX = (maxW - controlsPlaceable.width) / 2
            val controlsY = maxH - controlsH

            layout(maxW, maxH) {
                toolbarPlaceable.placeRelative(x = toolbarX, y = 0)
                coverPlaceable.placeRelative(x = coverX, y = coverY)
                controlsPlaceable.placeRelative(x = controlsX, y = controlsY)
            }
        }
    )
}