package moe.rukamori.archivetune

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.HazeState

@Immutable
internal data class ScaffoldHazeState(
    val hazeState: HazeState,
    val effectiveHazeState: HazeState?,
    val searchHazeState: HazeState,
    val effectiveSearchHazeState: HazeState?,
)

@Composable
internal fun rememberScaffoldHazeState(blurNavBar: Boolean): ScaffoldHazeState {
    val hazeState = remember { HazeState() }
    val effectiveHazeState = if (blurNavBar) hazeState else null
    val searchHazeState = remember { HazeState() }
    val effectiveSearchHazeState = if (blurNavBar) searchHazeState else null

    return remember(blurNavBar, hazeState, searchHazeState) {
        ScaffoldHazeState(
            hazeState = hazeState,
            effectiveHazeState = effectiveHazeState,
            searchHazeState = searchHazeState,
            effectiveSearchHazeState = effectiveSearchHazeState,
        )
    }
}
