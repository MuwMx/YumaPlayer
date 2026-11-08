package moe.rukamori.archivetune.ui.scaffold

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
    val searchHazeState = remember { HazeState() }

    return remember(hazeState, searchHazeState) {
        ScaffoldHazeState(
            hazeState = hazeState,
            effectiveHazeState = hazeState,
            searchHazeState = searchHazeState,
            effectiveSearchHazeState = searchHazeState,
        )
    }
}
